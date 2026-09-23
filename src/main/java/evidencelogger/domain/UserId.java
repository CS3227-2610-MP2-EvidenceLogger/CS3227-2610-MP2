package evidencelogger.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for an application user. */
public record UserId(UUID value) {
    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId parse(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
