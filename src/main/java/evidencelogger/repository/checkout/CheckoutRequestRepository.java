package evidencelogger.repository.checkout;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceId;

/** Persistence operations for checkout-request workflow use cases. */
public interface CheckoutRequestRepository {
    Optional<CheckoutRequestRecord> findById(Connection connection, CheckoutRequestId requestId);

    Optional<CheckoutRequestRecord> findPendingOrApprovedForEvidence(
            Connection connection, EvidenceId evidenceId);

    List<CheckoutRequestRecord> findForEvidence(Connection connection, EvidenceId evidenceId);

    void insertPending(Connection connection, CheckoutRequestRecord request);

    boolean transitionStatus(
            Connection connection,
            CheckoutRequestId requestId,
            CheckoutRequestStatus expected,
            CheckoutRequestStatus resulting);
}
