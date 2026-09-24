package evidencelogger.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.repository.EvidenceRecord;
import evidencelogger.repository.EvidenceRepository;
import evidencelogger.repository.RepositoryException;

/** SQLite/JDBC implementation of the evidence facts needed by checkout commands. */
public final class JdbcEvidenceRepository implements EvidenceRepository {
    @Override
    public Optional<EvidenceRecord> findById(Connection connection, EvidenceId evidenceId) {
        String sql = "SELECT id, case_id, custody_state FROM evidence_item WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, evidenceId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next()
                        ? Optional.of(new EvidenceRecord(
                                EvidenceId.parse(results.getString("id")),
                                CaseId.parse(results.getString("case_id")),
                                EvidenceCustodyState.valueOf(results.getString("custody_state"))))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new RepositoryException.StorageFailure(
                    "Unable to find evidence", exception);
        }
    }
}
