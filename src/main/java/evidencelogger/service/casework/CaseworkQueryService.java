package evidencelogger.service.casework;

import java.util.List;

import evidencelogger.domain.CaseId;
import evidencelogger.service.dto.CaseworkViews;

/** Authorized reads for case, assignment, location, and evidence data. */
public interface CaseworkQueryService {
    List<CaseworkViews.Case> searchCases(String searchText);

    List<CaseworkViews.Evidence> searchEvidence(String searchText);

    List<CaseworkViews.Investigator> listInvestigators();

    List<CaseworkViews.Investigator> listAssignments(CaseId caseId);

    List<CaseworkViews.StorageLocation> listStorageLocations();
}
