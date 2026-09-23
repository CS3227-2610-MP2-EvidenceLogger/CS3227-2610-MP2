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
        Optional<CheckoutRequestStatus> previousRequestStatus,
        Optional<CheckoutRequestStatus> resultingRequestStatus,
        Optional<EvidenceCustodyState> previousCustodyState,
        Optional<EvidenceCustodyState> resultingCustodyState,
        Optional<String> reasonOrComment,
        Optional<AuditEventId> correctedEventId,
        Optional<ExaminationNoteId> correctedNoteId) {

    public AuditEventDraft {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(handoffId, "handoffId");
        Objects.requireNonNull(checkoutId, "checkoutId");
        Objects.requireNonNull(previousRequestStatus, "previousRequestStatus");
        Objects.requireNonNull(resultingRequestStatus, "resultingRequestStatus");
        Objects.requireNonNull(previousCustodyState, "previousCustodyState");
        Objects.requireNonNull(resultingCustodyState, "resultingCustodyState");
        Objects.requireNonNull(reasonOrComment, "reasonOrComment");
        Objects.requireNonNull(correctedEventId, "correctedEventId");
        Objects.requireNonNull(correctedNoteId, "correctedNoteId");
    }
}
