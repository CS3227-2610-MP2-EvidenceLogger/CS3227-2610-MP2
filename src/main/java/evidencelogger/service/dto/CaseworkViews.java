package evidencelogger.service.dto;

import java.time.Instant;
import java.util.Objects;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.domain.UserId;

/** Immutable casework read models shared by role-specific views. */
public final class CaseworkViews {
    private CaseworkViews() {
    }

    /** Read model for a case. */
    public record Case(CaseId caseId, String title, Instant createdAt) {
        /** Validates that all case fields are present. */
        public Case {
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    /** Read model for an Investigator available for assignment. */
    public record Investigator(UserId investigatorId, String username, String displayName) {
        /** Validates that all Investigator fields are present. */
        public Investigator {
            Objects.requireNonNull(investigatorId, "investigatorId");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(displayName, "displayName");
        }
    }

    /** Read model for a managed storage location. */
    public record StorageLocation(
            StorageLocationId storageLocationId,
            String name,
            Instant createdAt) {
        /** Validates that all storage-location fields are present. */
        public StorageLocation {
            Objects.requireNonNull(storageLocationId, "storageLocationId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    /** Read model for a registered evidence item. */
    public record Evidence(
            EvidenceId evidenceId,
            CaseId caseId,
            String caseTitle,
            String publicReference,
            String description,
            StorageLocationId storageLocationId,
            String storageLocationName,
            EvidenceCustodyState custodyState,
            Instant registeredAt) {
        /** Validates that all evidence fields are present. */
        public Evidence {
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
}
