package evidencelogger.service.history;

import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.domain.UserId;

/**
 * Domain event data supplied by a service. The writer supplies the event ID,
 * current actor, actor role, and timestamp.
 */
public record AuditEventDraft(
        AuditEventType type,
        Optional<CaseId> caseId,
        Optional<EvidenceId> evidenceId,
        Optional<CheckoutRequestId> requestId,
        Optional<HandoffId> handoffId,
        Optional<CheckoutId> checkoutId,
        Optional<UserId> assignedInvestigatorId,
        Optional<StorageLocationId> storageLocationId,
        Optional<CheckoutRequestStatus> previousRequestStatus,
        Optional<CheckoutRequestStatus> resultingRequestStatus,
        Optional<EvidenceCustodyState> previousCustodyState,
        Optional<EvidenceCustodyState> resultingCustodyState,
        Optional<String> reason,
        Optional<String> comment,
        Optional<String> correctionText,
        Optional<AuditEventId> correctedEventId,
        Optional<ExaminationNoteId> correctedNoteId) {

    /** Creates an event for a checkout-request status transition. */
    public static AuditEventDraft requestTransition(
            AuditEventType type,
            CaseId caseId,
            EvidenceId evidenceId,
            CheckoutRequestId requestId,
            Optional<CheckoutRequestStatus> previousStatus,
            Optional<CheckoutRequestStatus> resultingStatus,
            EvidenceCustodyState custodyState,
            Optional<String> reason) {
        return new AuditEventDraft(
                type,
                Optional.of(caseId),
                Optional.of(evidenceId),
                Optional.of(requestId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                previousStatus,
                resultingStatus,
                Optional.of(custodyState),
                Optional.of(custodyState),
                reason,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /** Creates an event for a handoff or handoff-reversal transition. */
    public static AuditEventDraft handoffTransition(
            AuditEventType type,
            CaseId caseId,
            EvidenceId evidenceId,
            CheckoutRequestId requestId,
            HandoffId handoffId,
            CheckoutRequestStatus previousRequestStatus,
            CheckoutRequestStatus resultingRequestStatus,
            EvidenceCustodyState previousCustodyState,
            EvidenceCustodyState resultingCustodyState,
            Optional<String> reason) {
        return new AuditEventDraft(
                type,
                Optional.of(caseId),
                Optional.of(evidenceId),
                Optional.of(requestId),
                Optional.of(handoffId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(previousRequestStatus),
                Optional.of(resultingRequestStatus),
                Optional.of(previousCustodyState),
                Optional.of(resultingCustodyState),
                reason,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /** Creates an event for collection acknowledgment. */
    public static AuditEventDraft collectionAcknowledged(
            CaseId caseId,
            EvidenceId evidenceId,
            CheckoutRequestId requestId,
            HandoffId handoffId,
            CheckoutId checkoutId) {
        return new AuditEventDraft(
                AuditEventType.COLLECTION_ACKNOWLEDGED,
                Optional.of(caseId),
                Optional.of(evidenceId),
                Optional.of(requestId),
                Optional.of(handoffId),
                Optional.of(checkoutId),
                Optional.empty(),
                Optional.empty(),
                Optional.of(CheckoutRequestStatus.APPROVED),
                Optional.of(CheckoutRequestStatus.CONSUMED),
                Optional.of(EvidenceCustodyState.HANDOFF_AWAITING_ACK),
                Optional.of(EvidenceCustodyState.CHECKED_OUT),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /** Creates an event for adding or correcting an examination note. */
    public static AuditEventDraft examinationNoteChange(
            AuditEventType type,
            CaseId caseId,
            EvidenceId evidenceId,
            CheckoutId checkoutId,
            Optional<ExaminationNoteId> noteId,
            Optional<String> correctionText,
            Optional<String> reason,
            Optional<ExaminationNoteId> correctedNoteId) {
        return new AuditEventDraft(
                type,
                Optional.of(caseId),
                Optional.of(evidenceId),
                Optional.empty(),
                Optional.empty(),
                Optional.of(checkoutId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                reason,
                Optional.empty(),
                correctionText,
                Optional.empty(),
                correctedNoteId);
    }

    /** Creates an event for a custody-state transition associated with a checkout. */
    public static AuditEventDraft custodyTransition(
            AuditEventType type,
            CaseId caseId,
            EvidenceId evidenceId,
            CheckoutId checkoutId,
            EvidenceCustodyState previousState,
            EvidenceCustodyState resultingState,
            Optional<String> reason) {
        return new AuditEventDraft(
                type,
                Optional.of(caseId),
                Optional.of(evidenceId),
                Optional.empty(),
                Optional.empty(),
                Optional.of(checkoutId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(previousState),
                Optional.of(resultingState),
                reason,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /** Validates that optional event fields are represented explicitly. */
    public AuditEventDraft {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(handoffId, "handoffId");
        Objects.requireNonNull(checkoutId, "checkoutId");
        Objects.requireNonNull(assignedInvestigatorId, "assignedInvestigatorId");
        Objects.requireNonNull(storageLocationId, "storageLocationId");
        Objects.requireNonNull(previousRequestStatus, "previousRequestStatus");
        Objects.requireNonNull(resultingRequestStatus, "resultingRequestStatus");
        Objects.requireNonNull(previousCustodyState, "previousCustodyState");
        Objects.requireNonNull(resultingCustodyState, "resultingCustodyState");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(correctionText, "correctionText");
        Objects.requireNonNull(correctedEventId, "correctedEventId");
        Objects.requireNonNull(correctedNoteId, "correctedNoteId");
    }
}
