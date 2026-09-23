package evidencelogger.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for a managed evidence storage location. */
public record StorageLocationId(UUID value) {
    public StorageLocationId {
        Objects.requireNonNull(value, "value");
    }

    public static StorageLocationId parse(String value) {
        return new StorageLocationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
