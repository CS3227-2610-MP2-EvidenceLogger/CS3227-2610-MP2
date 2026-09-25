package evidencelogger.service.checkout;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.ReturnInspectionOutcome;
import evidencelogger.repository.EvidenceRecord;
import evidencelogger.repository.EvidenceRepository;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutRecord;
import evidencelogger.repository.checkout.CheckoutRepository;
import evidencelogger.repository.checkout.ReturnInspectionRecord;
import evidencelogger.repository.checkout.ReturnInspectionRepository;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.AuthorizationService;
import evidencelogger.service.dto.CheckoutCommands;
import evidencelogger.service.history.AuditEventDraft;
import evidencelogger.service.history.AuditEventWriter;

/** Coordinates return initiation and Custodian return inspections. */
final class CheckoutReturnWorkflow {
    private final AuthorizationService authorization;
    private final EvidenceRepository evidenceRepository;
    private final CheckoutRepository checkoutRepository;
    private final ReturnInspectionRepository inspectionRepository;
    private final AuditEventWriter auditEvents;
    private final Clock clock;

    CheckoutReturnWorkflow(
            AuthorizationService authorization,
            EvidenceRepository evidenceRepository,
            CheckoutRepository checkoutRepository,
            ReturnInspectionRepository inspectionRepository,
            AuditEventWriter auditEvents,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "evidenceRepository");
        this.checkoutRepository = Objects.requireNonNull(checkoutRepository, "checkoutRepository");
        this.inspectionRepository = Objects.requireNonNull(
                inspectionRepository, "inspectionRepository");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    void initiate(Connection connection, CheckoutCommands.InitiateReturn command) {
        CheckoutRecord checkout = findCheckout(connection, command.checkoutId());
        EvidenceRecord evidence = findEvidence(connection, checkout.evidenceId());
        authorization.requireCollectingInvestigator(connection, checkout.checkoutId());
        if (checkout.returnInitiatedAt().isPresent()
                || checkout.completedAt().isPresent()
                || checkout.evidenceState() != EvidenceCustodyState.CHECKED_OUT
                || evidence.custodyState() != EvidenceCustodyState.CHECKED_OUT) {
            throw new ServiceException.InvalidTransition(
                    "Only an active checked-out item may be returned");
        }
        try {
            Instant initiatedAt = Instant.now(clock);
            if (!checkoutRepository.markReturnInitiated(
                    connection, checkout.checkoutId(), initiatedAt)) {
                throw new ServiceException.InvalidTransition(
                        "Checkout return has already been initiated");
            }
            auditEvents.append(connection, AuditEventDraft.custodyTransition(
                    AuditEventType.RETURN_INITIATED,
                    evidence.caseId(),
                    evidence.evidenceId(),
                    checkout.checkoutId(),
                    EvidenceCustodyState.CHECKED_OUT,
                    EvidenceCustodyState.HANDIN_AWAITING_ACK,
                    Optional.empty()));
        } catch (RepositoryException exception) {
            throw CheckoutRepositoryErrors.translate(exception);
        }
    }

    void inspect(Connection connection, CheckoutCommands.InspectReturn command) {
        AuthenticatedSession custodian = authorization.requireCustodian();
        CheckoutRecord checkout = findCheckout(connection, command.checkoutId());
        EvidenceRecord evidence = findEvidence(connection, checkout.evidenceId());
        if (command.outcome() != ReturnInspectionOutcome.STORED
                || checkout.returnInitiatedAt().isEmpty()
                || checkout.completedAt().isPresent()
                || checkout.evidenceState() != EvidenceCustodyState.HANDIN_AWAITING_ACK
                || evidence.custodyState() != EvidenceCustodyState.HANDIN_AWAITING_ACK) {
            throw new ServiceException.InvalidTransition(
                    "Only an initiated return may be inspected and stored");
        }
        try {
            inspectionRepository.insert(connection, new ReturnInspectionRecord(
                    checkout.checkoutId(),
                    custodian.userId(),
                    command.outcome(),
                    false,
                    "",
                    Instant.now(clock)));
            if (!checkoutRepository.complete(
                    connection, checkout.checkoutId(), Instant.now(clock))) {
                throw new ServiceException.InvalidTransition(
                        "Return is no longer awaiting inspection");
            }
            appendInspectionAudit(connection, evidence, checkout.checkoutId(),
                    AuditEventType.RETURN_INSPECTED_STORED,
                    EvidenceCustodyState.HANDIN_AWAITING_ACK,
                    Optional.empty());
        } catch (RepositoryException exception) {
            throw CheckoutRepositoryErrors.translate(exception);
        }
    }

    void inspectUnplanned(
            Connection connection, CheckoutCommands.InspectUnplannedReturn command) {
        AuthenticatedSession custodian = authorization.requireCustodian();
        CheckoutRecord checkout = findCheckout(connection, command.checkoutId());
        EvidenceRecord evidence = findEvidence(connection, checkout.evidenceId());
        if (command.outcome() != ReturnInspectionOutcome.STORED
                || checkout.returnInitiatedAt().isPresent()
                || checkout.completedAt().isPresent()
                || checkout.evidenceState() != EvidenceCustodyState.CHECKED_OUT
                || evidence.custodyState() != EvidenceCustodyState.CHECKED_OUT) {
            throw new ServiceException.InvalidTransition(
                    "Only an active checked-out item may receive an unplanned return");
        }
        try {
            inspectionRepository.insert(connection, new ReturnInspectionRecord(
                    checkout.checkoutId(),
                    custodian.userId(),
                    command.outcome(),
                    true,
                    command.reason(),
                    Instant.now(clock)));
            if (!checkoutRepository.completeUnplanned(
                    connection, checkout.checkoutId(), Instant.now(clock))) {
                throw new ServiceException.InvalidTransition(
                        "Checkout is no longer available for unplanned return");
            }
            appendInspectionAudit(connection, evidence, checkout.checkoutId(),
                    AuditEventType.UNPLANNED_RETURN_INSPECTED,
                    EvidenceCustodyState.CHECKED_OUT,
                    Optional.of(command.reason()));
        } catch (RepositoryException exception) {
            throw CheckoutRepositoryErrors.translate(exception);
        }
    }

    private void appendInspectionAudit(
            Connection connection,
            EvidenceRecord evidence,
            CheckoutId checkoutId,
            AuditEventType type,
            EvidenceCustodyState previousState,
            Optional<String> reason) {
        auditEvents.append(connection, AuditEventDraft.custodyTransition(
                type,
                evidence.caseId(),
                evidence.evidenceId(),
                checkoutId,
                previousState,
                EvidenceCustodyState.IN_STORAGE,
                reason));
    }

    private CheckoutRecord findCheckout(Connection connection, CheckoutId checkoutId) {
        return checkoutRepository.findById(connection, checkoutId)
                .orElseThrow(() -> new ServiceException.NotFound("Checkout was not found"));
    }

    private EvidenceRecord findEvidence(
            Connection connection, evidencelogger.domain.EvidenceId evidenceId) {
        return evidenceRepository.findById(connection, evidenceId)
                .orElseThrow(() -> new ServiceException.NotFound("Evidence was not found"));
    }
}
