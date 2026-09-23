package evidencelogger.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for a recorded handoff. */
public record HandoffId(UUID value) {
    public HandoffId {
        Objects.requireNonNull(value, "value");
    }

    public static HandoffId parse(String value) {
        return new HandoffId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
