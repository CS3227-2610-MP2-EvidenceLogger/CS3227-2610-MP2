package evidencelogger.repository.checkout;

import java.sql.Connection;
import java.time.Instant;
import java.util.Optional;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.EvidenceId;

/** Persistence operations for acknowledged checkouts and return initiation. */
public interface CheckoutRepository {
    Optional<CheckoutRecord> findById(Connection connection, CheckoutId checkoutId);

    Optional<CheckoutRecord> findActiveForEvidence(Connection connection, EvidenceId evidenceId);

    void insert(Connection connection, CheckoutRecord checkout);

    boolean markReturnInitiated(Connection connection, CheckoutId checkoutId, Instant initiatedAt);

    boolean complete(Connection connection, CheckoutId checkoutId, Instant completedAt);

    boolean completeUnplanned(
            Connection connection, CheckoutId checkoutId, Instant completedAt);
}
