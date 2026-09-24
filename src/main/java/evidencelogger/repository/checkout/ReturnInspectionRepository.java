package evidencelogger.repository.checkout;

import java.sql.Connection;
import java.util.Optional;

import evidencelogger.domain.CheckoutId;

/** Persistence operations for Custodian confirmation of physical returns. */
public interface ReturnInspectionRepository {
    Optional<ReturnInspectionRecord> findByCheckout(Connection connection, CheckoutId checkoutId);

    void insert(Connection connection, ReturnInspectionRecord inspection);
}
