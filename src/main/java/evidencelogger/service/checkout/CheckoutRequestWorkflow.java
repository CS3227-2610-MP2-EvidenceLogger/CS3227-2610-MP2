package evidencelogger.service.checkout;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.repository.EvidenceRecord;
import evidencelogger.repository.EvidenceRepository;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutRequestRecord;
import evidencelogger.repository.checkout.CheckoutRequestRepository;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.AuthorizationService;
import evidencelogger.service.dto.CheckoutCommands;
import evidencelogger.service.history.AuditEventDraft;
import evidencelogger.service.history.AuditEventWriter;

/** Coordinates checkout-request validation, transitions, and audit events. */
final class CheckoutRequestWorkflow {
    private final AuthorizationService authorization;
    private final EvidenceRepository evidenceRepository;
    private final CheckoutRequestRepository requestRepository;
    private final AuditEventWriter auditEvents;
    private final IdGenerator<CheckoutRequestId> requestIds;
    private final Clock clock;

    CheckoutRequestWorkflow(
            AuthorizationService authorization,
            EvidenceRepository evidenceRepository,
            CheckoutRequestRepository requestRepository,
            AuditEventWriter auditEvents,
            IdGenerator<CheckoutRequestId> requestIds,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "evidenceRepository");
        this.requestRepository = Objects.requireNonNull(requestRepository, "requestRepository");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    CheckoutRequestId submit(
            Connection connection, CheckoutCommands.SubmitRequest command) {
        Instant submittedAt = Instant.now(clock);
        if (!command.expectedReturnAt().isAfter(submittedAt)) {
            throw new ServiceException.ValidationFailure(
                    "Expected return time must be later than submission time");
        }

        EvidenceRecord evidence = findEvidence(connection, command.evidenceId());
        AuthenticatedSession actor = requireAssigned(connection, evidence);
        if (evidence.custodyState() != EvidenceCustodyState.IN_STORAGE) {
            throw new ServiceException.InvalidTransition(
                    "Evidence is not available in storage for a checkout request");
        }
        if (requestRepository.findPendingOrApprovedForEvidence(
                connection, evidence.evidenceId()).isPresent()) {
            throw new ServiceException.Conflict(
                    "Evidence already has an active checkout request");
        }

        CheckoutRequestId requestId = requestIds.nextId();
        CheckoutRequestRecord request = new CheckoutRequestRecord(
                requestId,
                evidence.evidenceId(),
                actor.userId(),
                command.purpose(),
                command.expectedReturnAt(),
                CheckoutRequestStatus.PENDING,
                submittedAt);
        try {
            requestRepository.insertPending(connection, request);
            auditEvents.append(connection, AuditEventDraft.requestTransition(
                    AuditEventType.REQUEST_SUBMITTED,
                    evidence.caseId(),
                    evidence.evidenceId(),
                    requestId,
                    Optional.empty(),
                    Optional.of(CheckoutRequestStatus.PENDING),
                    evidence.custodyState(),
                    Optional.empty()));
            return requestId;
        } catch (RepositoryException exception) {
            throw CheckoutRepositoryErrors.translate(exception);
        }
    }

    void withdraw(Connection connection, CheckoutCommands.WithdrawRequest command) {
        CheckoutRequestRecord request = findRequest(connection, command.requestId());
        EvidenceRecord evidence = findEvidence(connection, request.evidenceId());
        AuthenticatedSession actor = requireAssigned(connection, evidence);
        if (!request.requesterId().equals(actor.userId())) {
            throw new ServiceException.Forbidden(
                    "Only the requesting Investigator may withdraw this request");
        }
        if (request.status() != CheckoutRequestStatus.PENDING) {
            throw new ServiceException.InvalidTransition(
                    "Only a pending checkout request may be withdrawn");
        }
        transitionAndAudit(
                connection,
                request,
                evidence,
                CheckoutRequestStatus.WITHDRAWN,
                AuditEventType.REQUEST_WITHDRAWN,
                Optional.of(CheckoutRequestStatus.PENDING),
                Optional.of(CheckoutRequestStatus.WITHDRAWN),
                Optional.empty());
    }

    void approve(Connection connection, CheckoutCommands.ApproveRequest command) {
        authorization.requireCustodian();
        CheckoutRequestRecord request = findRequest(connection, command.requestId());
        EvidenceRecord evidence = findEvidence(connection, request.evidenceId());
        if (request.status() != CheckoutRequestStatus.PENDING) {
            throw new ServiceException.InvalidTransition(
                    "Only a pending checkout request may be approved");
        }
        if (evidence.custodyState() != EvidenceCustodyState.IN_STORAGE) {
            throw new ServiceException.InvalidTransition(
                    "Evidence is not available in storage for approval");
        }
        requireRequesterAssigned(connection, request, evidence);
        assertActiveRequestIs(request, connection);
        transitionAndAudit(
                connection,
                request,
                evidence,
                CheckoutRequestStatus.APPROVED,
                AuditEventType.REQUEST_APPROVED,
                Optional.of(CheckoutRequestStatus.PENDING),
                Optional.of(CheckoutRequestStatus.APPROVED),
                Optional.empty());
    }

    void reject(Connection connection, CheckoutCommands.RejectRequest command) {
        authorization.requireCustodian();
        CheckoutRequestRecord request = findRequest(connection, command.requestId());
        EvidenceRecord evidence = findEvidence(connection, request.evidenceId());
        if (request.status() != CheckoutRequestStatus.PENDING) {
            throw new ServiceException.InvalidTransition(
                    "Only a pending checkout request may be rejected");
        }
        transitionAndAudit(
                connection,
                request,
                evidence,
                CheckoutRequestStatus.REJECTED,
                AuditEventType.REQUEST_REJECTED,
                Optional.of(CheckoutRequestStatus.PENDING),
                Optional.of(CheckoutRequestStatus.REJECTED),
                Optional.empty());
    }

    void cancel(Connection connection, CheckoutCommands.CancelApprovedRequest command) {
        authorization.requireCustodian();
        CheckoutRequestRecord request = findRequest(connection, command.requestId());
        EvidenceRecord evidence = findEvidence(connection, request.evidenceId());
        if (request.status() != CheckoutRequestStatus.APPROVED) {
            throw new ServiceException.InvalidTransition(
                    "Only an approved checkout request may be cancelled");
        }
        if (evidence.custodyState() != EvidenceCustodyState.IN_STORAGE) {
            throw new ServiceException.InvalidTransition(
                    "Evidence is not in storage for cancellation");
        }
        transitionAndAudit(
                connection,
                request,
                evidence,
                CheckoutRequestStatus.CANCELLED,
                AuditEventType.REQUEST_CANCELLED,
                Optional.of(CheckoutRequestStatus.APPROVED),
                Optional.of(CheckoutRequestStatus.CANCELLED),
                Optional.of(command.reason()));
    }

    private CheckoutRequestRecord findRequest(
            Connection connection, CheckoutRequestId requestId) {
        return requestRepository.findById(connection, requestId)
                .orElseThrow(() -> new ServiceException.NotFound(
                        "Checkout request was not found"));
    }

    private void assertActiveRequestIs(
            CheckoutRequestRecord request, Connection connection) {
        requestRepository.findPendingOrApprovedForEvidence(connection, request.evidenceId())
                .filter(active -> !active.requestId().equals(request.requestId()))
                .ifPresent(active -> {
                    throw new ServiceException.Conflict(
                            "Evidence already has another active checkout request");
                });
    }

    private void requireRequesterAssigned(
            Connection connection, CheckoutRequestRecord request, EvidenceRecord evidence) {
        authorization.requireAssignedInvestigator(
                connection, evidence.caseId(), request.requesterId());
    }

    private void transitionAndAudit(
            Connection connection,
            CheckoutRequestRecord request,
            EvidenceRecord evidence,
            CheckoutRequestStatus resultingStatus,
            AuditEventType eventType,
            Optional<CheckoutRequestStatus> previousStatus,
            Optional<CheckoutRequestStatus> resultingStatusValue,
            Optional<String> reason) {
        try {
            if (!requestRepository.transitionStatus(
                    connection,
                    request.requestId(),
                    request.status(),
                    resultingStatus)) {
                throw new ServiceException.InvalidTransition(
                        "Checkout request is no longer in the expected state");
            }
            auditEvents.append(connection, AuditEventDraft.requestTransition(
                    eventType,
                    evidence.caseId(),
                    evidence.evidenceId(),
                    request.requestId(),
                    previousStatus,
                    resultingStatusValue,
                    evidence.custodyState(),
                    reason));
        } catch (RepositoryException exception) {
            throw CheckoutRepositoryErrors.translate(exception);
        }
    }

    private EvidenceRecord findEvidence(
            Connection connection, evidencelogger.domain.EvidenceId evidenceId) {
        return evidenceRepository.findById(connection, evidenceId)
                .orElseThrow(() -> new ServiceException.NotFound("Evidence was not found"));
    }

    private AuthenticatedSession requireAssigned(
            Connection connection, EvidenceRecord evidence) {
        return authorization.requireAssignedInvestigator(connection, evidence.caseId());
    }
}
