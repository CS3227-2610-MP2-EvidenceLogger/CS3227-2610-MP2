package evidencelogger.repository.checkout;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.UserId;

/** Immutable persisted handoff record, including its append-only reversal data. */
public record HandoffRecord(
        HandoffId handoffId,
        CheckoutRequestId requestId,
        EvidenceId evidenceId,
        UserId custodianId,
        Instant recordedAt,
        Optional<Instant> acknowledgedAt,
        Optional<Instant> reversedAt,
        Optional<String> reversalReason) {
    /** Validates the persisted handoff fields. */
    public HandoffRecord {
        Objects.requireNonNull(handoffId, "handoffId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(custodianId, "custodianId");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(acknowledgedAt, "acknowledgedAt");
        Objects.requireNonNull(reversedAt, "reversedAt");
        Objects.requireNonNull(reversalReason, "reversalReason");
    }
}
