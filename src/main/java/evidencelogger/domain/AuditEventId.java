package evidencelogger.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for an append-only audit event. */
public record AuditEventId(UUID value) {
    public AuditEventId {
        Objects.requireNonNull(value, "value");
    }

    public static AuditEventId parse(String value) {
        return new AuditEventId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
