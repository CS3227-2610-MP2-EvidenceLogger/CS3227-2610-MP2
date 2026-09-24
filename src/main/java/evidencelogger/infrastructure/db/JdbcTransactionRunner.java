package evidencelogger.infrastructure.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import evidencelogger.service.ServiceException;

/** JDBC transaction runner that owns connection, commit, rollback, and cleanup. */
public final class JdbcTransactionRunner implements TransactionRunner {
    private final ConnectionFactory connectionFactory;

    /** Creates a transaction runner using the supplied connection factory. */
    public JdbcTransactionRunner(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    @Override
    public <T> T inTransaction(TransactionalWork<T> work) {
        Objects.requireNonNull(work, "work");
        try (Connection connection = connectionFactory.open()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            T result;
            try {
                result = work.execute(connection);
                connection.commit();
            } catch (SQLException | RuntimeException | Error failure) {
                rollback(connection, failure);
                restoreAutoCommit(connection, originalAutoCommit, failure);
                throw failure;
            }
            restoreAutoCommit(connection, originalAutoCommit);
            return result;
        } catch (SQLException exception) {
            throw new ServiceException.StorageFailure(
                    "The database transaction could not be completed", exception);
        }
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean originalAutoCommit)
            throws SQLException {
        if (connection.getAutoCommit() != originalAutoCommit) {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static void restoreAutoCommit(
            Connection connection, boolean originalAutoCommit, Throwable failure) {
        try {
            restoreAutoCommit(connection, originalAutoCommit);
        } catch (SQLException restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }
}
