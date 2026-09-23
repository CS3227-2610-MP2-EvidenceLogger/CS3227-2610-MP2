package evidencelogger.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.UserId;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutRecord;
import evidencelogger.repository.checkout.CheckoutRepository;

/** SQLite/JDBC implementation of acknowledged-checkout persistence use cases. */
public final class JdbcCheckoutRepository implements CheckoutRepository {
    private static final String SELECT_COLUMNS = "checkout_id, request_id, evidence_id, "
            + "collector_id, collected_at, return_initiated_at, completed_at, evidence_state";

    @Override
    public Optional<CheckoutRecord> findById(Connection connection, CheckoutId checkoutId) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM checkout WHERE checkout_id = ?";
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
        String sql = "SELECT " + SELECT_COLUMNS + " FROM checkout"
                + " WHERE evidence_id = ? AND completed_at IS NULL";
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
        String sql = "INSERT INTO checkout (" + SELECT_COLUMNS
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, checkout.checkoutId().toString());
            statement.setString(2, checkout.requestId().toString());
            statement.setString(3, checkout.evidenceId().toString());
            statement.setString(4, checkout.collectorId().toString());
            statement.setString(5, checkout.collectedAt().toString());
            setInstant(statement, 6, checkout.returnInitiatedAt());
            setInstant(statement, 7, checkout.completedAt());
            statement.setString(8, checkout.evidenceState().name());
            statement.executeUpdate();
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
        String sql = "UPDATE checkout SET return_initiated_at = ?, evidence_state = ?"
                + " WHERE checkout_id = ? AND return_initiated_at IS NULL"
                + " AND completed_at IS NULL AND evidence_state = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, initiatedAt.toString());
            statement.setString(2, EvidenceCustodyState.HANDIN_AWAITING_ACK.name());
            statement.setString(3, checkoutId.toString());
            statement.setString(4, EvidenceCustodyState.CHECKED_OUT.name());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw storageFailure("initiate checkout return", exception);
        }
    }

    @Override
    public boolean complete(Connection connection, CheckoutId checkoutId, Instant completedAt) {
        String sql = "UPDATE checkout SET completed_at = ?, evidence_state = ?"
                + " WHERE checkout_id = ? AND return_initiated_at IS NOT NULL"
                + " AND completed_at IS NULL AND evidence_state = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, completedAt.toString());
            statement.setString(2, EvidenceCustodyState.IN_STORAGE.name());
            statement.setString(3, checkoutId.toString());
            statement.setString(4, EvidenceCustodyState.HANDIN_AWAITING_ACK.name());
            return statement.executeUpdate() == 1;
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

    private static void setInstant(
            PreparedStatement statement, int index, Optional<Instant> instant) throws SQLException {
        if (instant.isPresent()) {
            statement.setString(index, instant.orElseThrow().toString());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private static boolean isConstraintViolation(SQLException exception) {
        return "23000".equals(exception.getSQLState())
                || String.valueOf(exception.getMessage()).toLowerCase().contains("constraint");
    }

    private static RepositoryException.StorageFailure storageFailure(
            String operation, SQLException exception) {
        return new RepositoryException.StorageFailure("Unable to " + operation, exception);
    }
}
