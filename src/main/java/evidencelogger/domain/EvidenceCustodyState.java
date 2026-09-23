package evidencelogger.domain;

/** Physical-custody workflow state for an evidence item. */
public enum EvidenceCustodyState {
    IN_STORAGE,
    HANDOFF_AWAITING_ACK,
    CHECKED_OUT,
    HANDIN_AWAITING_ACK;

    /**
     * Returns whether this custody state may advance directly to the target
     * state. Return initiation moves a checked-out item to
     * {@code HANDIN_AWAITING_ACK} until the Custodian confirms receipt.
     *
     * @param target the proposed next custody state
     * @return true when the custody state-machine transition is legal
     */
    public boolean canTransitionTo(EvidenceCustodyState target) {
        return switch (this) {
        case IN_STORAGE -> target == HANDOFF_AWAITING_ACK;
        case HANDOFF_AWAITING_ACK -> target == IN_STORAGE
                || target == CHECKED_OUT;
        case CHECKED_OUT -> target == HANDIN_AWAITING_ACK;
        case HANDIN_AWAITING_ACK -> target == IN_STORAGE;
        };
    }
}
