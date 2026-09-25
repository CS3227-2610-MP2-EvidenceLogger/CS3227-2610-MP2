package evidencelogger.service.casework;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.service.dto.CaseworkCommands;

/** Custodian-only commands for cases, assignments, locations, and evidence. */
public interface CaseworkCommandService {
    CaseId createCase(CaseworkCommands.CreateCase command);

    void addAssignment(CaseworkCommands.AddAssignment command);

    void removeAssignment(CaseworkCommands.RemoveAssignment command);

    StorageLocationId addStorageLocation(CaseworkCommands.AddStorageLocation command);

    EvidenceId registerEvidence(CaseworkCommands.RegisterEvidence command);
}
