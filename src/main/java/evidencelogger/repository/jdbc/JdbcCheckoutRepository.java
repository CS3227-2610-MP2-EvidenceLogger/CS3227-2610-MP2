package evidencelogger.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.UserId;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutRecord;
import evidencelogger.repository.checkout.CheckoutRepository;

/** SQLite/JDBC implementation of acknowledged-checkout persistence use cases. */
public final class JdbcCheckoutRepository implements CheckoutRepository {
    private static final String SELECT_COLUMNS = "c.id AS checkout_id, c.request_id, "
            + "c.evidence_id, c.collector_id, c.collected_at, c.return_initiated_at, "
            + "c.completed_at, e.custody_state AS evidence_state";
    private static final String SELECT_TABLES = " FROM checkout c"
            + " JOIN evidence_item e ON e.id = c.evidence_id";

    @Override
    public Optional<CheckoutRecord> findById(Connection connection, CheckoutId checkoutId) {
        String sql = "SELECT " + SELECT_COLUMNS + SELECT_TABLES + " WHERE c.id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, checkoutId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw storageFailure("find checkout", exception);
        }
    }

    @Override
    public Optional<CheckoutRecord> findActiveForEvidence(
            Connection connection, EvidenceId evidenceId) {
        String sql = "SELECT " + SELECT_COLUMNS + SELECT_TABLES
                + " WHERE c.evidence_id = ? AND c.completed_at IS NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, evidenceId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw storageFailure("find active checkout", exception);
        }
    }

    @Override
    public void insert(Connection connection, CheckoutRecord checkout) {
        if (checkout.returnInitiatedAt().isPresent()
                || checkout.completedAt().isPresent()
                || checkout.evidenceState() != EvidenceCustodyState.CHECKED_OUT) {
            throw new IllegalArgumentException("new checkout must be active and checked out");
        }
        String sql = "INSERT INTO checkout ("
                + "id, handoff_id, request_id, evidence_id, collector_id, collected_at)"
                + " SELECT ?, h.id, r.id, r.evidence_id, r.requester_id, ?"
                + " FROM handoff h JOIN checkout_request r ON r.id = h.request_id"
                + " WHERE r.id = ? AND r.evidence_id = ? AND r.requester_id = ?"
                + " AND r.status = ? AND h.evidence_id = r.evidence_id"
                + " AND h.acknowledged_at IS NOT NULL AND h.reversed_at IS NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, checkout.checkoutId().toString());
            statement.setString(2, checkout.collectedAt().toString());
            statement.setString(3, checkout.requestId().toString());
            statement.setString(4, checkout.evidenceId().toString());
            statement.setString(5, checkout.collectorId().toString());
            statement.setString(6, CheckoutRequestStatus.APPROVED.name());
            if (statement.executeUpdate() != 1) {
                throw new RepositoryException.Conflict(
                        "checkout requires an approved request and acknowledged matching handoff");
            }
            if (!transitionRequestToConsumed(connection, checkout.requestId())) {
                throw new RepositoryException.Conflict(
                        "checkout request is not approved for collection");
            }
            if (!transitionEvidenceState(
                    connection,
                    checkout.evidenceId(),
                    EvidenceCustodyState.HANDOFF_AWAITING_ACK,
                    EvidenceCustodyState.CHECKED_OUT)) {
                throw new RepositoryException.Conflict(
                        "evidence is not awaiting collection acknowledgment");
            }
        } catch (SQLException exception) {
            if (isConstraintViolation(exception)) {
                throw new RepositoryException.Conflict("checkout conflicts with an existing record");
            }
            throw storageFailure("insert checkout", exception);
        }
    }

    @Override
    public boolean markReturnInitiated(
            Connection connection, CheckoutId checkoutId, Instant initiatedAt) {
        String sql = "UPDATE checkout SET return_initiated_at = ?"
                + " WHERE id = ? AND return_initiated_at IS NULL AND completed_at IS NULL"
                + " AND EXISTS (SELECT 1 FROM evidence_item e"
                + " WHERE e.id = checkout.evidence_id AND e.custody_state = ?)"
                + " RETURNING evidence_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, initiatedAt.toString());
            statement.setString(2, checkoutId.toString());
            statement.setString(3, EvidenceCustodyState.CHECKED_OUT.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return false;
                }
                EvidenceId evidenceId = EvidenceId.parse(resultSet.getString("evidence_id"));
                if (!transitionEvidenceState(
                        connection,
                        evidenceId,
                        EvidenceCustodyState.CHECKED_OUT,
                        EvidenceCustodyState.HANDIN_AWAITING_ACK)) {
                    throw new RepositoryException.Conflict(
                            "evidence is not checked out");
                }
                return true;
            }
        } catch (SQLException exception) {
            throw storageFailure("initiate checkout return", exception);
        }
    }

    @Override
    public boolean complete(Connection connection, CheckoutId checkoutId, Instant completedAt) {
        String sql = "UPDATE checkout SET completed_at = ?, return_outcome = ?"
                + " WHERE id = ? AND return_initiated_at IS NOT NULL AND completed_at IS NULL"
                + " AND EXISTS (SELECT 1 FROM evidence_item e"
                + " WHERE e.id = checkout.evidence_id AND e.custody_state = ?)"
                + " RETURNING evidence_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, completedAt.toString());
            statement.setString(2, "STORED");
            statement.setString(3, checkoutId.toString());
            statement.setString(4, EvidenceCustodyState.HANDIN_AWAITING_ACK.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return false;
                }
                EvidenceId evidenceId = EvidenceId.parse(resultSet.getString("evidence_id"));
                if (!transitionEvidenceState(
                        connection,
                        evidenceId,
                        EvidenceCustodyState.HANDIN_AWAITING_ACK,
                        EvidenceCustodyState.IN_STORAGE)) {
                    throw new RepositoryException.Conflict(
                            "evidence is not awaiting return inspection");
                }
                return true;
            }
        } catch (SQLException exception) {
            throw storageFailure("complete checkout return", exception);
        }
    }

    private static CheckoutRecord map(ResultSet resultSet) throws SQLException {
        return new CheckoutRecord(
                CheckoutId.parse(resultSet.getString("checkout_id")),
                CheckoutRequestId.parse(resultSet.getString("request_id")),
                EvidenceId.parse(resultSet.getString("evidence_id")),
                UserId.parse(resultSet.getString("collector_id")),
                Instant.parse(resultSet.getString("collected_at")),
                getInstant(resultSet, "return_initiated_at"),
                getInstant(resultSet, "completed_at"),
                EvidenceCustodyState.valueOf(resultSet.getString("evidence_state")));
    }

    private static Optional<Instant> getInstant(ResultSet resultSet, String column)
            throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? Optional.empty() : Optional.of(Instant.parse(value));
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

    private static boolean transitionRequestToConsumed(
            Connection connection, CheckoutRequestId requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE checkout_request SET status = ? WHERE id = ? AND status = ?")) {
            statement.setString(1, CheckoutRequestStatus.CONSUMED.name());
            statement.setString(2, requestId.toString());
            statement.setString(3, CheckoutRequestStatus.APPROVED.name());
            return statement.executeUpdate() == 1;
        }
    }

    private static RepositoryException.StorageFailure storageFailure(
            String operation, SQLException exception) {
        return new RepositoryException.StorageFailure("Unable to " + operation, exception);
    }
}
