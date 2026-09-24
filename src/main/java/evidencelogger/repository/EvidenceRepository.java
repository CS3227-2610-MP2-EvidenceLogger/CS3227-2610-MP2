package evidencelogger.repository;

import java.sql.Connection;
import java.util.Optional;

import evidencelogger.domain.EvidenceId;

/** Persistence operations for loading evidence facts used by workflow commands. */
public interface EvidenceRepository {
    Optional<EvidenceRecord> findById(Connection connection, EvidenceId evidenceId);
}
