package evidencelogger.service.history;

import evidencelogger.service.dto.HistoryCommands;

/** Custodian commands that append documentary corrections to audit history. */
public interface HistoryCommandService {
    void correctEvent(HistoryCommands.CorrectEvent command);
}
