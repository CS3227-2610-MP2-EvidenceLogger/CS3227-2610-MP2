package evidencelogger.service.dto;

import java.util.Objects;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.domain.UserId;

/** Technology-neutral command inputs for case and evidence management. */
public final class CaseworkCommands {
    private CaseworkCommands() {
    }

    /** Input for creating a case with its required initial assignment. */
    public record CreateCase(String title, UserId initialInvestigatorId) {
        /** Validates the required initial Investigator. */
        public CreateCase {
            Objects.requireNonNull(initialInvestigatorId, "initialInvestigatorId");
        }
    }

    /** Input for assigning an Investigator to an existing case. */
    public record AddAssignment(CaseId caseId, UserId investigatorId) {
        /** Validates the assignment identifiers. */
        public AddAssignment {
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(investigatorId, "investigatorId");
        }
    }

    /** Input for removing an Investigator from a case. */
    public record RemoveAssignment(CaseId caseId, UserId investigatorId) {
        /** Validates the assignment identifiers. */
        public RemoveAssignment {
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(investigatorId, "investigatorId");
        }
    }

    /** Input for adding a selectable storage location. */
    public record AddStorageLocation(String name) {
    }

    /** Input for registering one evidence item in storage. */
    public record RegisterEvidence(
            CaseId caseId,
            String description,
            StorageLocationId storageLocationId) {
        /** Validates the evidence relationship identifiers. */
        public RegisterEvidence {
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(storageLocationId, "storageLocationId");
        }
    }
}
