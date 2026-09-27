package evidencelogger.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.Role;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.ConnectionFactory;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.casework.CaseRecord;
import evidencelogger.repository.casework.CaseworkRepository;
import evidencelogger.repository.casework.EvidenceRecord;
import evidencelogger.repository.casework.InvestigatorRecord;
import evidencelogger.repository.casework.StorageLocationRecord;

/** SQLite/JDBC persistence for casework commands and authorized queries. */
public final class JdbcCaseworkRepository implements CaseworkRepository {
    private final ConnectionFactory connectionFactory;

    /** Creates a casework repository using short-lived connections for reads. */
    public JdbcCaseworkRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    @Override
    public boolean caseExists(Connection connection, CaseId caseId) {
        return exists(connection, "SELECT 1 FROM case_record WHERE id = ?", caseId.toString());
    }

    @Override
    public boolean investigatorExists(Connection connection, UserId investigatorId) {
        return exists(connection, "SELECT 1 FROM user_account WHERE id = ? AND role = ?",
                investigatorId.toString(), Role.INVESTIGATOR.name());
    }

    @Override
    public boolean storageLocationExists(
            Connection connection, StorageLocationId storageLocationId) {
        return exists(connection, "SELECT 1 FROM storage_location WHERE id = ?",
                storageLocationId.toString());
    }

    @Override
    public boolean assignmentExists(
            Connection connection, CaseId caseId, UserId investigatorId) {
        return exists(connection, """
                SELECT 1 FROM case_assignment
                WHERE case_id = ? AND investigator_id = ?
                """, caseId.toString(), investigatorId.toString());
    }

    @Override
    public void insertCase(Connection connection, CaseRecord caseRecord) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO case_record(id, title, created_at) VALUES (?, ?, ?)
                """)) {
            statement.setString(1, caseRecord.caseId().toString());
            statement.setString(2, caseRecord.title());
            statement.setString(3, caseRecord.createdAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw translateWriteFailure("insert case", exception);
        }
    }

    @Override
    public void insertAssignment(
            Connection connection,
            CaseId caseId,
            UserId investigatorId,
            Instant assignedAt) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO case_assignment(case_id, investigator_id, assigned_at)
                VALUES (?, ?, ?)
                """)) {
            statement.setString(1, caseId.toString());
            statement.setString(2, investigatorId.toString());
            statement.setString(3, assignedAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw translateWriteFailure("insert case assignment", exception);
        }
    }

    @Override
    public boolean removeAssignmentIfInactive(
            Connection connection, CaseId caseId, UserId investigatorId) {
        String sql = """
                DELETE FROM case_assignment
                WHERE case_id = ? AND investigator_id = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM checkout_request r
                      JOIN evidence_item e ON e.id = r.evidence_id
                      WHERE e.case_id = case_assignment.case_id
                        AND r.requester_id = case_assignment.investigator_id
                        AND r.status IN ('PENDING', 'APPROVED')
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM checkout c
                      JOIN evidence_item e ON e.id = c.evidence_id
                      WHERE e.case_id = case_assignment.case_id
                        AND c.collector_id = case_assignment.investigator_id
                        AND c.completed_at IS NULL
                  )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, caseId.toString());
            statement.setString(2, investigatorId.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw storageFailure("remove case assignment", exception);
        }
    }

    @Override
    public void insertStorageLocation(
            Connection connection, StorageLocationRecord storageLocation) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO storage_location(id, name, created_at) VALUES (?, ?, ?)
                """)) {
            statement.setString(1, storageLocation.storageLocationId().toString());
            statement.setString(2, storageLocation.name());
            statement.setString(3, storageLocation.createdAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw translateWriteFailure("insert storage location", exception);
        }
    }

    @Override
    public void insertEvidence(
            Connection connection,
            EvidenceId evidenceId,
            CaseId caseId,
            String publicReference,
            String description,
            StorageLocationId storageLocationId,
            EvidenceCustodyState custodyState,
            Instant registeredAt) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO evidence_item(
                    id, case_id, public_reference, description,
                    storage_location_id, custody_state, registered_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, evidenceId.toString());
            statement.setString(2, caseId.toString());
            statement.setString(3, publicReference);
            statement.setString(4, description);
            statement.setString(5, storageLocationId.toString());
            statement.setString(6, custodyState.name());
            statement.setString(7, registeredAt.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw translateWriteFailure("insert evidence", exception);
        }
    }

    @Override
    public Optional<EvidenceRecord> findEvidence(
            Connection connection, EvidenceId evidenceId) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT e.id, e.case_id, c.title AS case_title, e.public_reference,
                       e.description, e.storage_location_id, l.name AS location_name,
                       e.custody_state, e.registered_at
                FROM evidence_item e
                JOIN case_record c ON c.id = e.case_id
                JOIN storage_location l ON l.id = e.storage_location_id
                WHERE e.id = ?
                """)) {
            statement.setString(1, evidenceId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next()
                        ? Optional.of(mapEvidence(results))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw storageFailure("find evidence", exception);
        }
    }

    @Override
    public boolean voidEvidenceIfEligible(
            Connection connection, EvidenceId evidenceId) {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE evidence_item
                SET custody_state = 'VOIDED'
                WHERE id = ?
                  AND custody_state = 'IN_STORAGE'
                  AND NOT EXISTS (
                      SELECT 1 FROM checkout_request request
                      WHERE request.evidence_id = evidence_item.id
                  )
                """)) {
            statement.setString(1, evidenceId.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw storageFailure("void evidence", exception);
        }
    }

    @Override
    public List<CaseRecord> searchCases(
            String searchText, Optional<UserId> assignedInvestigatorId) {
        String assignmentJoin = assignedInvestigatorId.isPresent()
                ? " JOIN case_assignment a ON a.case_id = c.id AND a.investigator_id = ?"
                : "";
        String sql = "SELECT c.id, c.title, c.created_at FROM case_record c"
                + assignmentJoin
                + " WHERE instr(lower(c.title), lower(?)) > 0"
                + " ORDER BY lower(c.title), c.id";
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int searchIndex = setOptionalInvestigator(
                        statement, assignedInvestigatorId, 1);
                statement.setString(searchIndex, searchText);
                try (ResultSet results = statement.executeQuery()) {
                    List<CaseRecord> cases = new ArrayList<>();
                    while (results.next()) {
                        cases.add(new CaseRecord(
                                CaseId.parse(results.getString("id")),
                                results.getString("title"),
                                Instant.parse(results.getString("created_at"))));
                    }
                    return List.copyOf(cases);
                }
            }
        }, "search cases");
    }

    @Override
    public List<EvidenceRecord> searchEvidence(
            String searchText,
            Optional<UserId> assignedInvestigatorId,
            boolean includeVoided) {
        String assignmentJoin = assignedInvestigatorId.isPresent()
                ? " JOIN case_assignment a ON a.case_id = c.id AND a.investigator_id = ?"
                : "";
        String sql = """
                SELECT e.id, e.case_id, c.title AS case_title, e.public_reference,
                       e.description, e.storage_location_id, l.name AS location_name,
                       e.custody_state, e.registered_at
                FROM evidence_item e
                JOIN case_record c ON c.id = e.case_id
                JOIN storage_location l ON l.id = e.storage_location_id
                """ + assignmentJoin + """
                 WHERE (? OR e.custody_state <> 'VOIDED')
                   AND (instr(lower(e.public_reference), lower(?)) > 0
                    OR instr(lower(e.description), lower(?)) > 0
                    OR instr(lower(c.title), lower(?)) > 0
                    OR instr(lower(l.name), lower(?)) > 0)
                 ORDER BY lower(e.public_reference), e.id
                """;
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int searchIndex = setOptionalInvestigator(
                        statement, assignedInvestigatorId, 1);
                statement.setBoolean(searchIndex, includeVoided);
                statement.setString(searchIndex + 1, searchText);
                statement.setString(searchIndex + 2, searchText);
                statement.setString(searchIndex + 3, searchText);
                statement.setString(searchIndex + 4, searchText);
                try (ResultSet results = statement.executeQuery()) {
                    List<EvidenceRecord> evidence = new ArrayList<>();
                    while (results.next()) {
                        evidence.add(mapEvidence(results));
                    }
                    return List.copyOf(evidence);
                }
            }
        }, "search evidence");
    }

    @Override
    public List<InvestigatorRecord> listInvestigators() {
        return queryInvestigators("""
                SELECT id, username, display_name
                FROM user_account
                WHERE role = ?
                ORDER BY lower(display_name), id
                """, Optional.empty());
    }

    @Override
    public List<InvestigatorRecord> listAssignments(CaseId caseId) {
        return queryInvestigators("""
                SELECT u.id, u.username, u.display_name
                FROM user_account u
                JOIN case_assignment a ON a.investigator_id = u.id
                WHERE a.case_id = ?
                ORDER BY lower(u.display_name), u.id
                """, Optional.of(caseId));
    }

    @Override
    public List<StorageLocationRecord> listStorageLocations() {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, name, created_at
                    FROM storage_location
                    ORDER BY lower(name), id
                    """); ResultSet results = statement.executeQuery()) {
                List<StorageLocationRecord> locations = new ArrayList<>();
                while (results.next()) {
                    locations.add(new StorageLocationRecord(
                            StorageLocationId.parse(results.getString("id")),
                            results.getString("name"),
                            Instant.parse(results.getString("created_at"))));
                }
                return List.copyOf(locations);
            }
        }, "list storage locations");
    }

    private List<InvestigatorRecord> queryInvestigators(
            String sql, Optional<CaseId> caseId) {
        return withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (caseId.isPresent()) {
                    statement.setString(1, caseId.orElseThrow().toString());
                } else {
                    statement.setString(1, Role.INVESTIGATOR.name());
                }
                try (ResultSet results = statement.executeQuery()) {
                    List<InvestigatorRecord> investigators = new ArrayList<>();
                    while (results.next()) {
                        investigators.add(new InvestigatorRecord(
                                UserId.parse(results.getString("id")),
                                results.getString("username"),
                                results.getString("display_name")));
                    }
                    return List.copyOf(investigators);
                }
            }
        }, "list Investigators");
    }

    private <T> T withConnection(ConnectionQuery<T> query, String operation) {
        try (Connection connection = connectionFactory.open()) {
            return query.execute(connection);
        } catch (SQLException exception) {
            throw storageFailure(operation, exception);
        }
    }

    private static boolean exists(Connection connection, String sql, String... parameters) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setString(index + 1, parameters[index]);
            }
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        } catch (SQLException exception) {
            throw storageFailure("read casework data", exception);
        }
    }

    private static int setOptionalInvestigator(
            PreparedStatement statement,
            Optional<UserId> investigatorId,
            int firstIndex) throws SQLException {
        if (investigatorId.isPresent()) {
            statement.setString(firstIndex, investigatorId.orElseThrow().toString());
            return firstIndex + 1;
        }
        return firstIndex;
    }

    private static EvidenceRecord mapEvidence(ResultSet results) throws SQLException {
        return new EvidenceRecord(
                EvidenceId.parse(results.getString("id")),
                CaseId.parse(results.getString("case_id")),
                results.getString("case_title"),
                results.getString("public_reference"),
                results.getString("description"),
                StorageLocationId.parse(results.getString("storage_location_id")),
                results.getString("location_name"),
                EvidenceCustodyState.valueOf(results.getString("custody_state")),
                Instant.parse(results.getString("registered_at")));
    }

    private static RepositoryException translateWriteFailure(
            String operation, SQLException exception) {
        if (isConstraintViolation(exception)) {
            return new RepositoryException.Conflict(
                    "The casework change conflicts with existing data");
        }
        return storageFailure(operation, exception);
    }

    private static boolean isConstraintViolation(SQLException exception) {
        return "23000".equals(exception.getSQLState())
                || String.valueOf(exception.getMessage()).toLowerCase().contains("constraint");
    }

    private static RepositoryException.StorageFailure storageFailure(
            String operation, SQLException exception) {
        return new RepositoryException.StorageFailure("Unable to " + operation, exception);
    }

    @FunctionalInterface
    private interface ConnectionQuery<T> {
        T execute(Connection connection) throws SQLException;
    }
}
