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
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (RuntimeException | Error failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                restoreAutoCommit(connection, originalAutoCommit);
            }
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
}
