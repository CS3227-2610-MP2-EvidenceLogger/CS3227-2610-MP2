package evidencelogger.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable internal identifier for an evidence item. */
public record EvidenceId(UUID value) {
    public EvidenceId {
        Objects.requireNonNull(value, "value");
    }

    public static EvidenceId parse(String value) {
        return new EvidenceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
