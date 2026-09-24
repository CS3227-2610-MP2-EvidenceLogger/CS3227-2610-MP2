package evidencelogger.repository.checkout;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.UserId;

/** Immutable persisted record for one acknowledged checkout. */
public record CheckoutRecord(
        CheckoutId checkoutId,
        CheckoutRequestId requestId,
        EvidenceId evidenceId,
        UserId collectorId,
        Instant collectedAt,
        Optional<Instant> returnInitiatedAt,
        Optional<Instant> completedAt,
        EvidenceCustodyState evidenceState) {
    /** Validates the persisted checkout fields. */
    public CheckoutRecord {
        Objects.requireNonNull(checkoutId, "checkoutId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(collectorId, "collectorId");
        Objects.requireNonNull(collectedAt, "collectedAt");
        Objects.requireNonNull(returnInitiatedAt, "returnInitiatedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(evidenceState, "evidenceState");
    }
}
