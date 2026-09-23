package evidencelogger.repository.checkout;

import java.sql.Connection;
import java.util.Optional;

import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.HandoffId;

/** Persistence operations for handoff recording, acknowledgment, and reversal. */
public interface HandoffRepository {
    Optional<HandoffRecord> findById(Connection connection, HandoffId handoffId);

    Optional<HandoffRecord> findUnacknowledgedForRequest(
            Connection connection, CheckoutRequestId requestId);

    void insert(Connection connection, HandoffRecord handoff);

    boolean acknowledge(Connection connection, HandoffId handoffId);

    boolean reverse(Connection connection, HandoffId handoffId, String reason);
}
