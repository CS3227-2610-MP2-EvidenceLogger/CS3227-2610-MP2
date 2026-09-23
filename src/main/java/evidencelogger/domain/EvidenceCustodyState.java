package evidencelogger.domain;

/** Physical-custody workflow state for an evidence item. */
public enum EvidenceCustodyState {
    IN_STORAGE,
    HANDOFF_AWAITING_ACK,
    CHECKED_OUT,
    HELD_FOR_REVIEW
}
