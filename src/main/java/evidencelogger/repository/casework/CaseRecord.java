package evidencelogger.repository.casework;

import java.time.Instant;
import java.util.Objects;

import evidencelogger.domain.CaseId;

/** Immutable persisted case data. */
public record CaseRecord(CaseId caseId, String title, Instant createdAt) {
    /** Validates that all persisted case fields are present. */
    public CaseRecord {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
