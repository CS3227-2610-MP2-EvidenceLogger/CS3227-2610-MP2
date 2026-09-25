package evidencelogger.repository;

import java.util.Objects;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;

/** Immutable evidence facts needed by checkout workflow services. */
public record EvidenceRecord(
        EvidenceId evidenceId,
        CaseId caseId,
        EvidenceCustodyState custodyState) {
    /** Validates the evidence identity and current workflow state. */
    public EvidenceRecord {
        Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(custodyState, "custodyState");
    }
}
