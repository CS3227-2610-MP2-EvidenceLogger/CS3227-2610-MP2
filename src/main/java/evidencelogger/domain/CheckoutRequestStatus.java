package evidencelogger.domain;

/** Permission state for a checkout request. */
public enum CheckoutRequestStatus {
    PENDING,
    APPROVED,
    CONSUMED,
    REJECTED,
    WITHDRAWN,
    CANCELLED;

    /**
     * Returns whether this request status may advance directly to the target
     * status. A status cannot transition to itself.
     *
     * @param target the proposed next status
     * @return true when the state-machine transition is legal
     */
    public boolean canTransitionTo(CheckoutRequestStatus target) {
        return switch (this) {
        case PENDING -> target == APPROVED
                || target == REJECTED
                || target == WITHDRAWN;
        case APPROVED -> target == CONSUMED || target == CANCELLED;
        case CONSUMED, REJECTED, WITHDRAWN, CANCELLED -> false;
        };
    }

    /**
     * Returns whether this request status is terminal.
     *
     * @return true when no further request transition is legal
     */
    public boolean isTerminal() {
        return switch (this) {
        case CONSUMED, REJECTED, WITHDRAWN, CANCELLED -> true;
        case PENDING, APPROVED -> false;
        };
    }
}
