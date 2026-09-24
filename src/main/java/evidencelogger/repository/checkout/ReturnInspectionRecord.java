package evidencelogger.repository.checkout;

import java.time.Instant;
import java.util.Objects;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.ReturnInspectionOutcome;
import evidencelogger.domain.UserId;

/** Immutable persisted record of Custodian confirmation of a returned checkout. */
public record ReturnInspectionRecord(
        CheckoutId checkoutId,
        UserId custodianId,
        ReturnInspectionOutcome outcome,
        boolean unplanned,
        String reason,
        Instant inspectedAt) {
    /** Validates the persisted return-inspection fields. */
    public ReturnInspectionRecord {
        Objects.requireNonNull(checkoutId, "checkoutId");
        Objects.requireNonNull(custodianId, "custodianId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(inspectedAt, "inspectedAt");
    }
}
