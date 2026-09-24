package evidencelogger.repository.checkout;

import java.time.Instant;
import java.util.Objects;

import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.UserId;

/** Immutable persisted checkout-request record. */
public record CheckoutRequestRecord(
        CheckoutRequestId requestId,
        EvidenceId evidenceId,
        UserId requesterId,
        String purpose,
        Instant expectedReturnAt,
        CheckoutRequestStatus status,
        Instant submittedAt) {
    /** Validates the persisted request fields. */
    public CheckoutRequestRecord {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(expectedReturnAt, "expectedReturnAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(submittedAt, "submittedAt");
    }
}
