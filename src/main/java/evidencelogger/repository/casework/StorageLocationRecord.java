package evidencelogger.repository.casework;

import java.time.Instant;
import java.util.Objects;

import evidencelogger.domain.StorageLocationId;

/** Immutable persisted storage-location data. */
public record StorageLocationRecord(
        StorageLocationId storageLocationId, String name, Instant createdAt) {
    /** Validates that all persisted storage-location fields are present. */
    public StorageLocationRecord {
        Objects.requireNonNull(storageLocationId, "storageLocationId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
