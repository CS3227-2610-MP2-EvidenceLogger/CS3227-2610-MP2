package evidencelogger.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.UserId;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutRequestRecord;
import evidencelogger.repository.checkout.CheckoutRequestRepository;

/** SQLite/JDBC implementation of checkout-request persistence use cases. */
public final class JdbcCheckoutRequestRepository implements CheckoutRequestRepository {
    private static final String SELECT_COLUMNS = "request_id, evidence_id, requester_id, "
            + "purpose, expected_return_at, status, submitted_at";

    @Override
    public Optional<CheckoutRequestRecord> findById(
            Connection connection, CheckoutRequestId requestId) {
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM checkout_request WHERE request_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requestId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(map(resultSet))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw storageFailure("find checkout request", exception);
        }
    }

    @Override
    public Optional<CheckoutRequestRecord> findPendingOrApprovedForEvidence(
            Connection connection, EvidenceId evidenceId) {
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM checkout_request WHERE evidence_id = ?"
                + " AND status IN (?, ?) ORDER BY submitted_at, request_id LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, evidenceId.toString());
            statement.setString(2, CheckoutRequestStatus.PENDING.name());
            statement.setString(3, CheckoutRequestStatus.APPROVED.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(map(resultSet))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw storageFailure("find active checkout request", exception);
        }
    }

    @Override
    public List<CheckoutRequestRecord> findForEvidence(
            Connection connection, EvidenceId evidenceId) {
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM checkout_request WHERE evidence_id = ?"
                + " ORDER BY submitted_at, request_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, evidenceId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<CheckoutRequestRecord> requests = new ArrayList<>();
                while (resultSet.next()) {
                    requests.add(map(resultSet));
                }
                return List.copyOf(requests);
            }
        } catch (SQLException exception) {
            throw storageFailure("list checkout requests", exception);
        }
    }

    @Override
    public void insertPending(Connection connection, CheckoutRequestRecord request) {
        String sql = "INSERT INTO checkout_request ("
                + SELECT_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, request.requestId().toString());
            statement.setString(2, request.evidenceId().toString());
            statement.setString(3, request.requesterId().toString());
            statement.setString(4, request.purpose());
            statement.setString(5, request.expectedReturnAt().toString());
            statement.setString(6, request.status().name());
            statement.setString(7, request.submittedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if (isConstraintViolation(exception)) {
                throw new RepositoryException.Conflict(
                        "checkout request conflicts with an existing record");
            }
            throw storageFailure("insert checkout request", exception);
        }
    }

    @Override
    public boolean transitionStatus(
            Connection connection,
            CheckoutRequestId requestId,
            CheckoutRequestStatus expected,
            CheckoutRequestStatus resulting) {
        String sql = "UPDATE checkout_request SET status = ?"
                + " WHERE request_id = ? AND status = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, resulting.name());
            statement.setString(2, requestId.toString());
            statement.setString(3, expected.name());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw storageFailure("transition checkout request", exception);
        }
    }

    private static CheckoutRequestRecord map(ResultSet resultSet) throws SQLException {
        return new CheckoutRequestRecord(
                CheckoutRequestId.parse(resultSet.getString("request_id")),
                EvidenceId.parse(resultSet.getString("evidence_id")),
                UserId.parse(resultSet.getString("requester_id")),
                resultSet.getString("purpose"),
                Instant.parse(resultSet.getString("expected_return_at")),
                CheckoutRequestStatus.valueOf(resultSet.getString("status")),
                Instant.parse(resultSet.getString("submitted_at")));
    }

    private static boolean isConstraintViolation(SQLException exception) {
        return "23000".equals(exception.getSQLState())
                || String.valueOf(exception.getMessage()).toLowerCase().contains("constraint");
    }

    private static RepositoryException.StorageFailure storageFailure(
            String operation, SQLException exception) {
        return new RepositoryException.StorageFailure(
                "Unable to " + operation, exception);
    }
}
