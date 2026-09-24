package evidencelogger.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.ReturnInspectionOutcome;
import evidencelogger.domain.UserId;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.ReturnInspectionRecord;
import evidencelogger.repository.checkout.ReturnInspectionRepository;

/** SQLite/JDBC implementation of Custodian return-inspection persistence. */
public final class JdbcReturnInspectionRepository implements ReturnInspectionRepository {
    private static final String SELECT_COLUMNS = "checkout_id, custodian_id, outcome, "
            + "unplanned, reason, inspected_at";

    @Override
    public Optional<ReturnInspectionRecord> findByCheckout(
            Connection connection, CheckoutId checkoutId) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM return_inspection"
                + " WHERE checkout_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, checkoutId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw storageFailure("find return inspection", exception);
        }
    }

    @Override
    public void insert(Connection connection, ReturnInspectionRecord inspection) {
        String sql = "INSERT INTO return_inspection (" + SELECT_COLUMNS
                + ") VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, inspection.checkoutId().toString());
            statement.setString(2, inspection.custodianId().toString());
            statement.setString(3, inspection.outcome().name());
            statement.setBoolean(4, inspection.unplanned());
            statement.setString(5, inspection.reason());
            statement.setString(6, inspection.inspectedAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if (isConstraintViolation(exception)) {
                throw new RepositoryException.Conflict(
                        "return inspection conflicts with an existing record");
            }
            throw storageFailure("insert return inspection", exception);
        }
    }

    private static ReturnInspectionRecord map(ResultSet resultSet) throws SQLException {
        return new ReturnInspectionRecord(
                CheckoutId.parse(resultSet.getString("checkout_id")),
                UserId.parse(resultSet.getString("custodian_id")),
                ReturnInspectionOutcome.valueOf(resultSet.getString("outcome")),
                resultSet.getBoolean("unplanned"),
                resultSet.getString("reason"),
                Instant.parse(resultSet.getString("inspected_at")));
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
