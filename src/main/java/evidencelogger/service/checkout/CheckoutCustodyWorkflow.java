package evidencelogger.service.checkout;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.HandoffId;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.repository.EvidenceRecord;
import evidencelogger.repository.EvidenceRepository;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutRecord;
import evidencelogger.repository.checkout.CheckoutRepository;
import evidencelogger.repository.checkout.CheckoutRequestRecord;
import evidencelogger.repository.checkout.CheckoutRequestRepository;
import evidencelogger.repository.checkout.HandoffRecord;
import evidencelogger.repository.checkout.HandoffRepository;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.AuthorizationService;
import evidencelogger.service.dto.CheckoutCommands;
import evidencelogger.service.history.AuditEventDraft;
import evidencelogger.service.history.AuditEventWriter;

/** Coordinates handoff, reversal, and collection acknowledgment. */
final class CheckoutCustodyWorkflow {
    private final AuthorizationService authorization;
    private final EvidenceRepository evidenceRepository;
    private final CheckoutRequestRepository requestRepository;
    private final HandoffRepository handoffRepository;
    private final CheckoutRepository checkoutRepository;
    private final AuditEventWriter auditEvents;
    private final IdGenerator<HandoffId> handoffIds;
    private final IdGenerator<CheckoutId> checkoutIds;
    private final Clock clock;

    CheckoutCustodyWorkflow(
            AuthorizationService authorization,
            EvidenceRepository evidenceRepository,
            CheckoutRequestRepository requestRepository,
            HandoffRepository handoffRepository,
            CheckoutRepository checkoutRepository,
            AuditEventWriter auditEvents,
            IdGenerator<HandoffId> handoffIds,
            IdGenerator<CheckoutId> checkoutIds,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "evidenceRepository");
        this.requestRepository = Objects.requireNonNull(requestRepository, "requestRepository");
        this.handoffRepository = Objects.requireNonNull(handoffRepository, "handoffRepository");
        this.checkoutRepository = Objects.requireNonNull(checkoutRepository, "checkoutRepository");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.handoffIds = Objects.requireNonNull(handoffIds, "handoffIds");
        this.checkoutIds = Objects.requireNonNull(checkoutIds, "checkoutIds");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    HandoffId recordHandoff(
            Connection connection, CheckoutCommands.RecordHandoff command) {
        AuthenticatedSession custodian = authorization.requireCustodian();
        CheckoutRequestRecord request = findRequest(connection, command.requestId());
        EvidenceRecord evidence = findEvidence(connection, request.evidenceId());
        if (request.status() != CheckoutRequestStatus.APPROVED) {
            throw new ServiceException.InvalidTransition(
                    "Only an approved checkout request may be handed off");
        }
        if (evidence.custodyState() != EvidenceCustodyState.IN_STORAGE) {
            throw new ServiceException.InvalidTransition(
                    "Evidence is not available in storage for handoff");
        }
        requireRequesterAssigned(connection, request, evidence);
        if (handoffRepository.findUnacknowledgedForRequest(
                connection, request.requestId()).isPresent()) {
            throw new ServiceException.Conflict(
                    "The request already has an unacknowledged handoff");
        }

        HandoffId handoffId = handoffIds.nextId();
        HandoffRecord handoff = new HandoffRecord(
                handoffId,
                request.requestId(),
                evidence.evidenceId(),
                custodian.userId(),
                Instant.now(clock),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        try {
            handoffRepository.insert(connection, handoff);
            auditEvents.append(connection, AuditEventDraft.handoffTransition(
                    AuditEventType.HANDOFF_RECORDED,
                    evidence.caseId(),
                    evidence.evidenceId(),
                    request.requestId(),
                    handoffId,
                    CheckoutRequestStatus.APPROVED,
                    CheckoutRequestStatus.APPROVED,
                    EvidenceCustodyState.IN_STORAGE,
                    EvidenceCustodyState.HANDOFF_AWAITING_ACK,
                    Optional.empty()));
            return handoffId;
        } catch (RepositoryException exception) {
            throw CheckoutRepositoryErrors.translate(exception);
        }
    }

    void reverseHandoff(
            Connection connection, CheckoutCommands.ReverseHandoff command) {
        authorization.requireCustodian();
        HandoffRecord handoff = handoffRepository.findById(connection, command.handoffId())
                .orElseThrow(() -> new ServiceException.NotFound("Handoff was not found"));
        CheckoutRequestRecord request = findRequest(connection, handoff.requestId());
        EvidenceRecord evidence = findEvidence(connection, handoff.evidenceId());
        if (request.status() != CheckoutRequestStatus.APPROVED
                || handoff.acknowledgedAt().isPresent()
                || handoff.reversedAt().isPresent()
                || evidence.custodyState() != EvidenceCustodyState.HANDOFF_AWAITING_ACK) {
            throw new ServiceException.InvalidTransition(
                    "Only an unacknowledged handoff may be reversed");
        }
        try {
            if (!handoffRepository.reverse(
                    connection,
                    handoff.handoffId(),
                    command.reason(),
                    Instant.now(clock))) {
                throw new ServiceException.InvalidTransition(
                        "Handoff is no longer awaiting acknowledgment");
            }
            auditEvents.append(connection, AuditEventDraft.handoffTransition(
                    AuditEventType.HANDOFF_REVERSED,
                    evidence.caseId(),
                    evidence.evidenceId(),
                    request.requestId(),
                    handoff.handoffId(),
                    CheckoutRequestStatus.APPROVED,
                    CheckoutRequestStatus.CANCELLED,
                    EvidenceCustodyState.HANDOFF_AWAITING_ACK,
                    EvidenceCustodyState.IN_STORAGE,
                    Optional.of(command.reason())));
        } catch (RepositoryException exception) {
            throw CheckoutRepositoryErrors.translate(exception);
        }
    }

    CheckoutId acknowledgeCollection(
            Connection connection, CheckoutCommands.AcknowledgeCollection command) {
        HandoffRecord handoff = handoffRepository.findById(connection, command.handoffId())
                .orElseThrow(() -> new ServiceException.NotFound("Handoff was not found"));
        CheckoutRequestRecord request = findRequest(connection, handoff.requestId());
        EvidenceRecord evidence = findEvidence(connection, handoff.evidenceId());
        AuthenticatedSession actor = authorization.requireAssignedInvestigator(
                connection, evidence.caseId());
        if (!request.requesterId().equals(actor.userId())) {
            throw new ServiceException.Forbidden(
                    "Only the requesting Investigator may acknowledge collection");
        }
        if (request.status() != CheckoutRequestStatus.APPROVED
                || handoff.acknowledgedAt().isPresent()
                || handoff.reversedAt().isPresent()
                || evidence.custodyState() != EvidenceCustodyState.HANDOFF_AWAITING_ACK) {
            throw new ServiceException.InvalidTransition(
                    "Only an approved unacknowledged handoff may be collected");
        }

        CheckoutId checkoutId = checkoutIds.nextId();
        Instant collectedAt = Instant.now(clock);
        try {
            if (!handoffRepository.acknowledge(
                    connection, handoff.handoffId(), collectedAt)) {
                throw new ServiceException.InvalidTransition(
                        "Handoff is no longer awaiting acknowledgment");
            }
            checkoutRepository.insert(connection, new CheckoutRecord(
                    checkoutId,
                    request.requestId(),
                    evidence.evidenceId(),
                    actor.userId(),
                    collectedAt,
                    Optional.empty(),
                    Optional.empty(),
                    EvidenceCustodyState.CHECKED_OUT));
            auditEvents.append(connection, AuditEventDraft.collectionAcknowledged(
                    evidence.caseId(),
                    evidence.evidenceId(),
                    request.requestId(),
                    handoff.handoffId(),
                    checkoutId));
            return checkoutId;
        } catch (RepositoryException exception) {
            throw CheckoutRepositoryErrors.translate(exception);
        }
    }

    private CheckoutRequestRecord findRequest(
            Connection connection, CheckoutRequestId requestId) {
        return requestRepository.findById(connection, requestId)
                .orElseThrow(() -> new ServiceException.NotFound(
                        "Checkout request was not found"));
    }

    private EvidenceRecord findEvidence(
            Connection connection, evidencelogger.domain.EvidenceId evidenceId) {
        return evidenceRepository.findById(connection, evidenceId)
                .orElseThrow(() -> new ServiceException.NotFound("Evidence was not found"));
    }

    private void requireRequesterAssigned(
            Connection connection, CheckoutRequestRecord request, EvidenceRecord evidence) {
        authorization.requireAssignedInvestigator(
                connection, evidence.caseId(), request.requesterId());
    }
}
