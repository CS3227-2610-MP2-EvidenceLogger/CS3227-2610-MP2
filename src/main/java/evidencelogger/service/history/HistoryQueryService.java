package evidencelogger.service.history;

import java.util.List;

import evidencelogger.domain.CaseId;
import evidencelogger.service.dto.HistoryViews;

/** Authorized reads of append-only case history. */
public interface HistoryQueryService {
    List<HistoryViews.Event> listEventsForCase(CaseId caseId);
}
