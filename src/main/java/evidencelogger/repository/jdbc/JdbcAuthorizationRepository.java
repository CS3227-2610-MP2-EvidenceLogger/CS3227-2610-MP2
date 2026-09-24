package evidencelogger.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.ConnectionFactory;
import evidencelogger.repository.AuthorizationRepository;
import evidencelogger.service.ServiceException;

/** JDBC-backed current-assignment and collector authorization facts. */
public final class JdbcAuthorizationRepository implements AuthorizationRepository {
    private final ConnectionFactory connectionFactory;

    /** Creates an authorization repository using short-lived read connections. */
    public JdbcAuthorizationRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    @Override
    public boolean isAssigned(CaseId caseId, UserId investigatorId) {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(investigatorId, "investigatorId");
        return withConnection(connection -> isAssigned(connection, caseId, investigatorId));
    }

    @Override
    public boolean isAssigned(
            Connection connection, CaseId caseId, UserId investigatorId) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(investigatorId, "investigatorId");
        return exists(connection, """
                        SELECT 1
                        FROM case_assignment
                        WHERE case_id = ? AND investigator_id = ?
                        """,
                caseId.toString(), investigatorId.toString());
    }

    @Override
    public boolean isCollectingInvestigator(CheckoutId checkoutId, UserId investigatorId) {
        Objects.requireNonNull(checkoutId, "checkoutId");
        Objects.requireNonNull(investigatorId, "investigatorId");
        return withConnection(connection ->
                isCollectingInvestigator(connection, checkoutId, investigatorId));
    }

    @Override
    public boolean isCollectingInvestigator(
            Connection connection, CheckoutId checkoutId, UserId investigatorId) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(checkoutId, "checkoutId");
        Objects.requireNonNull(investigatorId, "investigatorId");
        return exists(connection, """
                        SELECT 1
                        FROM checkout c
                        JOIN evidence_item e ON e.id = c.evidence_id
                        JOIN case_assignment a ON a.case_id = e.case_id
                        WHERE c.id = ? AND c.collector_id = ? AND a.investigator_id = ?
                        """,
                checkoutId.toString(), investigatorId.toString(), investigatorId.toString());
    }

    private boolean withConnection(ConnectionQuery query) {
        try (Connection connection = connectionFactory.open()) {
            return query.execute(connection);
        } catch (SQLException exception) {
            throw storageFailure(exception);
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
            throw storageFailure(exception);
        }
    }

    private static ServiceException.StorageFailure storageFailure(SQLException exception) {
        return new ServiceException.StorageFailure(
                "Authorization data could not be read", exception);
    }

    @FunctionalInterface
    private interface ConnectionQuery {
        boolean execute(Connection connection);
    }
}
