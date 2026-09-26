package evidencelogger.service.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.Role;

/** Immutable audit-history read models shared by authorized role views. */
public final class HistoryViews {
    private HistoryViews() {
    }

    /** One append-only event shown in timestamp and stable-ID order. */
    public record Event(
            AuditEventId eventId,
            AuditEventType type,
            String actorDisplayName,
            Role actorRole,
            Instant eventTime,
            Optional<EvidenceId> evidenceId,
            Optional<String> evidenceReference,
            Optional<CheckoutRequestId> requestId,
            Optional<HandoffId> handoffId,
            Optional<CheckoutId> checkoutId,
            Optional<CheckoutRequestStatus> previousRequestStatus,
            Optional<CheckoutRequestStatus> resultingRequestStatus,
            Optional<EvidenceCustodyState> previousCustodyState,
            Optional<EvidenceCustodyState> resultingCustodyState,
            Optional<String> correctionText,
            Optional<String> reason,
            Optional<AuditEventId> correctedEventId) {
        /** Validates immutable display fields and explicit optional values. */
        public Event {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(actorDisplayName, "actorDisplayName");
            Objects.requireNonNull(actorRole, "actorRole");
            Objects.requireNonNull(eventTime, "eventTime");
            Objects.requireNonNull(evidenceId, "evidenceId");
            Objects.requireNonNull(evidenceReference, "evidenceReference");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(handoffId, "handoffId");
            Objects.requireNonNull(checkoutId, "checkoutId");
            Objects.requireNonNull(previousRequestStatus, "previousRequestStatus");
            Objects.requireNonNull(resultingRequestStatus, "resultingRequestStatus");
            Objects.requireNonNull(previousCustodyState, "previousCustodyState");
            Objects.requireNonNull(resultingCustodyState, "resultingCustodyState");
            Objects.requireNonNull(correctionText, "correctionText");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(correctedEventId, "correctedEventId");
        }
    }
}
