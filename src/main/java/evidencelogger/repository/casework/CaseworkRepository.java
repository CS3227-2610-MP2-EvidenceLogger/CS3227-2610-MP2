package evidencelogger.repository.casework;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.domain.UserId;

/** Persistence operations needed by casework commands and authorized reads. */
public interface CaseworkRepository {
    boolean caseExists(Connection connection, CaseId caseId);

    boolean investigatorExists(Connection connection, UserId investigatorId);

    boolean storageLocationExists(Connection connection, StorageLocationId storageLocationId);

    boolean assignmentExists(Connection connection, CaseId caseId, UserId investigatorId);

    void insertCase(Connection connection, CaseRecord caseRecord);

    void insertAssignment(
            Connection connection, CaseId caseId, UserId investigatorId, Instant assignedAt);

    boolean removeAssignmentIfInactive(
            Connection connection, CaseId caseId, UserId investigatorId);

    void insertStorageLocation(Connection connection, StorageLocationRecord storageLocation);

    void insertEvidence(
            Connection connection,
            EvidenceId evidenceId,
            CaseId caseId,
            String publicReference,
            String description,
            StorageLocationId storageLocationId,
            EvidenceCustodyState custodyState,
            Instant registeredAt);

    List<CaseRecord> searchCases(String searchText, Optional<UserId> assignedInvestigatorId);

    List<EvidenceRecord> searchEvidence(
            String searchText, Optional<UserId> assignedInvestigatorId);

    List<InvestigatorRecord> listInvestigators();

    List<InvestigatorRecord> listAssignments(CaseId caseId);

    List<StorageLocationRecord> listStorageLocations();
}
