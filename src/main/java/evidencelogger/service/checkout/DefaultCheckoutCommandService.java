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
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.repository.AuthorizationRepository;
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

/** Transactional checkout commands. B3 request commands are implemented first. */
public final class DefaultCheckoutCommandService implements CheckoutCommandService {
    private final TransactionRunner transactions;
    private final AuthorizationService authorization;
    private final AuthorizationRepository authorizationRepository;
    private final EvidenceRepository evidenceRepository;
    private final CheckoutRequestRepository requestRepository;
    private final AuditEventWriter auditEvents;
    private final IdGenerator<CheckoutRequestId> requestIds;
    private final Clock clock;

    /** Creates checkout commands with all application collaborators injected. */
    public DefaultCheckoutCommandService(
            TransactionRunner transactions,
            AuthorizationService authorization,
            AuthorizationRepository authorizationRepository,
            EvidenceRepository evidenceRepository,
            CheckoutRequestRepository requestRepository,
            AuditEventWriter auditEvents,
            IdGenerator<CheckoutRequestId> requestIds,
            Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.authorizationRepository = Objects.requireNonNull(
                authorizationRepository, "authorizationRepository");
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "evidenceRepository");
        this.requestRepository = Objects.requireNonNull(requestRepository, "requestRepository");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CheckoutRequestId submitRequest(CheckoutCommands.SubmitRequest command) {
        Objects.requireNonNull(command, "command");
        return transactions.inTransaction(connection -> submit(connection, command));
    }

    private CheckoutRequestId submit(
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
            auditEvents.append(connection, requestEvent(
                    AuditEventType.REQUEST_SUBMITTED,
                    evidence,
                    requestId,
                    Optional.empty(),
                    Optional.of(CheckoutRequestStatus.PENDING)));
            return requestId;
        } catch (RepositoryException exception) {
            throw translate(exception);
        }
    }

    @Override
    public void withdrawRequest(CheckoutCommands.WithdrawRequest command) {
        Objects.requireNonNull(command, "command");
        transactions.inTransaction(connection -> {
            withdraw(connection, command);
            return null;
        });
    }

    private void withdraw(Connection connection, CheckoutCommands.WithdrawRequest command) {
        CheckoutRequestRecord request = requestRepository.findById(connection, command.requestId())
                .orElseThrow(() -> new ServiceException.NotFound("Checkout request was not found"));
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
        try {
            if (!requestRepository.transitionStatus(
                    connection,
                    request.requestId(),
                    CheckoutRequestStatus.PENDING,
                    CheckoutRequestStatus.WITHDRAWN)) {
                throw new ServiceException.InvalidTransition(
                        "Checkout request is no longer pending");
            }
            auditEvents.append(connection, requestEvent(
                    AuditEventType.REQUEST_WITHDRAWN,
                    evidence,
                    request.requestId(),
                    Optional.of(CheckoutRequestStatus.PENDING),
                    Optional.of(CheckoutRequestStatus.WITHDRAWN)));
        } catch (RepositoryException exception) {
            throw translate(exception);
        }
    }

    private EvidenceRecord findEvidence(Connection connection,
            evidencelogger.domain.EvidenceId evidenceId) {
        return evidenceRepository.findById(connection, evidenceId)
                .orElseThrow(() -> new ServiceException.NotFound("Evidence was not found"));
    }

    private AuthenticatedSession requireAssigned(
            Connection connection, EvidenceRecord evidence) {
        return authorization.requireAssignedInvestigator(
                evidence.caseId(),
                (caseId, investigatorId) -> authorizationRepository.isAssigned(
                        connection, caseId, investigatorId));
    }

    private static AuditEventDraft requestEvent(
            AuditEventType type,
            EvidenceRecord evidence,
            CheckoutRequestId requestId,
            Optional<CheckoutRequestStatus> previous,
            Optional<CheckoutRequestStatus> resulting) {
        return new AuditEventDraft(
                type,
                Optional.of(evidence.caseId()),
                Optional.of(evidence.evidenceId()),
                Optional.of(requestId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                previous,
                resulting,
                Optional.of(evidence.custodyState()),
                Optional.of(evidence.custodyState()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static ServiceException translate(RepositoryException exception) {
        if (exception instanceof RepositoryException.Conflict) {
            return new ServiceException.Conflict(exception.getMessage());
        }
        if (exception instanceof RepositoryException.NotFound) {
            return new ServiceException.NotFound(exception.getMessage());
        }
        return new ServiceException.StorageFailure(exception.getMessage(), exception);
    }

    private static ServiceException notImplemented(String action) {
        return new ServiceException.InvalidTransition(
                action + " is not implemented yet");
    }

    @Override
    public void approveRequest(CheckoutCommands.ApproveRequest command) {
        throw notImplemented("Request approval");
    }

    @Override
    public void rejectRequest(CheckoutCommands.RejectRequest command) {
        throw notImplemented("Request rejection");
    }

    @Override
    public void cancelApprovedRequest(CheckoutCommands.CancelApprovedRequest command) {
        throw notImplemented("Request cancellation");
    }

    @Override
    public evidencelogger.domain.HandoffId recordHandoff(CheckoutCommands.RecordHandoff command) {
        throw notImplemented("Handoff recording");
    }

    @Override
    public void reverseHandoff(CheckoutCommands.ReverseHandoff command) {
        throw notImplemented("Handoff reversal");
    }

    @Override
    public evidencelogger.domain.CheckoutId acknowledgeCollection(
            CheckoutCommands.AcknowledgeCollection command) {
        throw notImplemented("Collection acknowledgment");
    }

    @Override
    public evidencelogger.domain.ExaminationNoteId addExaminationNote(
            CheckoutCommands.AddExaminationNote command) {
        throw notImplemented("Examination notes");
    }

    @Override
    public void correctExaminationNote(CheckoutCommands.CorrectExaminationNote command) {
        throw notImplemented("Examination-note correction");
    }

    @Override
    public void initiateReturn(CheckoutCommands.InitiateReturn command) {
        throw notImplemented("Return initiation");
    }

    @Override
    public void inspectReturn(CheckoutCommands.InspectReturn command) {
        throw notImplemented("Return inspection");
    }

    @Override
    public void inspectUnplannedReturn(CheckoutCommands.InspectUnplannedReturn command) {
        throw notImplemented("Unplanned-return inspection");
    }
}
