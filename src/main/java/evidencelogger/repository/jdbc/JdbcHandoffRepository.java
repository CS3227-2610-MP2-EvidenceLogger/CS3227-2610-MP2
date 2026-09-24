package evidencelogger.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.UserId;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.HandoffRecord;
import evidencelogger.repository.checkout.HandoffRepository;

/** SQLite/JDBC implementation of handoff persistence use cases. */
public final class JdbcHandoffRepository implements HandoffRepository {
    private static final String SELECT_COLUMNS = "id AS handoff_id, request_id, evidence_id, "
            + "custodian_id, recorded_at, acknowledged_at, reversed_at, reversal_reason";

    @Override
    public Optional<HandoffRecord> findById(Connection connection, HandoffId handoffId) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM handoff WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, handoffId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw storageFailure("find handoff", exception);
        }
    }

    @Override
    public Optional<HandoffRecord> findUnacknowledgedForRequest(
            Connection connection, CheckoutRequestId requestId) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM handoff"
                + " WHERE request_id = ? AND acknowledged_at IS NULL"
                + " AND reversed_at IS NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requestId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw storageFailure("find unacknowledged handoff", exception);
        }
    }

    @Override
    public void insert(Connection connection, HandoffRecord handoff) {
        String sql = "INSERT INTO handoff ("
                + "id, request_id, evidence_id, custodian_id, recorded_at, "
                + "acknowledged_at, reversed_at, reversal_reason)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, handoff.handoffId().toString());
            statement.setString(2, handoff.requestId().toString());
            statement.setString(3, handoff.evidenceId().toString());
            statement.setString(4, handoff.custodianId().toString());
            statement.setString(5, handoff.recordedAt().toString());
            setInstant(statement, 6, handoff.acknowledgedAt());
            setInstant(statement, 7, handoff.reversedAt());
            setOptionalText(statement, 8, handoff.reversalReason());
            statement.executeUpdate();
            if (!transitionEvidenceState(
                    connection,
                    handoff.evidenceId(),
                    EvidenceCustodyState.IN_STORAGE,
                    EvidenceCustodyState.HANDOFF_AWAITING_ACK)) {
                throw new RepositoryException.Conflict(
                        "evidence is not available for handoff");
            }
        } catch (SQLException exception) {
            if (isConstraintViolation(exception)) {
                throw new RepositoryException.Conflict("handoff conflicts with an existing record");
            }
            throw storageFailure("insert handoff", exception);
        }
    }

    @Override
    public boolean acknowledge(Connection connection, HandoffId handoffId, Instant acknowledgedAt) {
        String sql = "UPDATE handoff SET acknowledged_at = ?"
                + " WHERE id = ? AND acknowledged_at IS NULL AND reversed_at IS NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, acknowledgedAt.toString());
            statement.setString(2, handoffId.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw storageFailure("acknowledge handoff", exception);
        }
    }

    @Override
    public boolean reverse(
            Connection connection, HandoffId handoffId, String reason, Instant reversedAt) {
        String sql = "UPDATE handoff SET reversed_at = ?, reversal_reason = ?"
                + " WHERE id = ? AND acknowledged_at IS NULL AND reversed_at IS NULL"
                + " RETURNING evidence_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reversedAt.toString());
            statement.setString(2, reason);
            statement.setString(3, handoffId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return false;
                }
                EvidenceId evidenceId = EvidenceId.parse(resultSet.getString("evidence_id"));
                if (!transitionEvidenceState(
                        connection,
                        evidenceId,
                        EvidenceCustodyState.HANDOFF_AWAITING_ACK,
                        EvidenceCustodyState.IN_STORAGE)) {
                    throw new RepositoryException.Conflict(
                            "evidence is not awaiting handoff acknowledgment");
                }
                return true;
            }
        } catch (SQLException exception) {
            throw storageFailure("reverse handoff", exception);
        }
    }

    private static HandoffRecord map(ResultSet resultSet) throws SQLException {
        return new HandoffRecord(
                HandoffId.parse(resultSet.getString("handoff_id")),
                CheckoutRequestId.parse(resultSet.getString("request_id")),
                EvidenceId.parse(resultSet.getString("evidence_id")),
                UserId.parse(resultSet.getString("custodian_id")),
                Instant.parse(resultSet.getString("recorded_at")),
                getInstant(resultSet, "acknowledged_at"),
                getInstant(resultSet, "reversed_at"),
                Optional.ofNullable(resultSet.getString("reversal_reason")));
    }

    private static Optional<Instant> getInstant(ResultSet resultSet, String column)
            throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? Optional.empty() : Optional.of(Instant.parse(value));
    }

    private static void setInstant(
            PreparedStatement statement, int index, Optional<Instant> instant) throws SQLException {
        if (instant.isPresent()) {
            statement.setString(index, instant.orElseThrow().toString());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private static void setOptionalText(
            PreparedStatement statement, int index, Optional<String> value) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private static boolean isConstraintViolation(SQLException exception) {
        return "23000".equals(exception.getSQLState())
                || String.valueOf(exception.getMessage()).toLowerCase().contains("constraint");
    }

    private static boolean transitionEvidenceState(
            Connection connection,
            EvidenceId evidenceId,
            EvidenceCustodyState expected,
            EvidenceCustodyState resulting) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE evidence_item SET custody_state = ? WHERE id = ? AND custody_state = ?")) {
            statement.setString(1, resulting.name());
            statement.setString(2, evidenceId.toString());
            statement.setString(3, expected.name());
            return statement.executeUpdate() == 1;
        }
    }

    private static RepositoryException.StorageFailure storageFailure(
            String operation, SQLException exception) {
        return new RepositoryException.StorageFailure("Unable to " + operation, exception);
    }
}
