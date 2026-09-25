package evidencelogger.repository.casework;

import java.time.Instant;
import java.util.Objects;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.StorageLocationId;

/** Immutable persisted evidence data used by registration and queries. */
public record EvidenceRecord(
        EvidenceId evidenceId,
        CaseId caseId,
        String caseTitle,
        String publicReference,
        String description,
        StorageLocationId storageLocationId,
        String storageLocationName,
        EvidenceCustodyState custodyState,
        Instant registeredAt) {
    /** Validates that all persisted evidence fields are present. */
    public EvidenceRecord {
        Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(caseTitle, "caseTitle");
        Objects.requireNonNull(publicReference, "publicReference");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(storageLocationId, "storageLocationId");
        Objects.requireNonNull(storageLocationName, "storageLocationName");
        Objects.requireNonNull(custodyState, "custodyState");
        Objects.requireNonNull(registeredAt, "registeredAt");
    }
}
