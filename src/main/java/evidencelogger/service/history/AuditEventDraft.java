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
 * Domain event data supplied by a service. The writer supplies the event ID and
 * timestamp and persists the actor authorized by the service.
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
