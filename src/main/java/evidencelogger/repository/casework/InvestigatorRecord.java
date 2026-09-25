package evidencelogger.repository.casework;

import java.util.Objects;

import evidencelogger.domain.UserId;

/** Immutable Investigator identity used by assignment queries. */
public record InvestigatorRecord(UserId investigatorId, String username, String displayName) {
    /** Validates that all Investigator fields are present. */
    public InvestigatorRecord {
        Objects.requireNonNull(investigatorId, "investigatorId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
    }
}
