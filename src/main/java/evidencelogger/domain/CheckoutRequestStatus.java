package evidencelogger.domain;

/** Permission state for a checkout request. */
public enum CheckoutRequestStatus {
    PENDING,
    APPROVED,
    CONSUMED,
    REJECTED,
    WITHDRAWN,
    CANCELLED
}
