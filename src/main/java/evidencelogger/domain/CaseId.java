package evidencelogger.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for a case. */
public record CaseId(UUID value) {
    public CaseId {
        Objects.requireNonNull(value, "value");
    }

    public static CaseId parse(String value) {
        return new CaseId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
