package evidencelogger.infrastructure.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import evidencelogger.service.ServiceException;

/** JDBC transaction runner that owns connection, commit, rollback, and cleanup. */
public final class JdbcTransactionRunner implements TransactionRunner {
    private static final Logger LOGGER = Logger.getLogger(JdbcTransactionRunner.class.getName());

    private final ConnectionFactory connectionFactory;

    /** Creates a transaction runner using the supplied connection factory. */
    public JdbcTransactionRunner(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    @Override
    public <T> T inTransaction(TransactionalWork<T> work) {
        Objects.requireNonNull(work, "work");
        Connection connection = openConnection();
        boolean originalAutoCommit;
        try {
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException failure) {
            close(connection, failure);
            throw storageFailure(failure);
        }
        try {
            T result = work.execute(connection);
            connection.commit();
            restoreAfterCommit(connection, originalAutoCommit);
            closeAfterCommit(connection);
            return result;
        } catch (SQLException failure) {
            cleanupAfterFailure(connection, originalAutoCommit, failure);
            throw storageFailure(failure);
        } catch (RuntimeException | Error failure) {
            cleanupAfterFailure(connection, originalAutoCommit, failure);
            throw failure;
        }
    }

    private Connection openConnection() {
        try {
            return connectionFactory.open();
        } catch (SQLException exception) {
            throw storageFailure(exception);
        }
    }

    private static void cleanupAfterFailure(
            Connection connection, boolean originalAutoCommit, Throwable failure) {
        rollback(connection, failure);
        restoreAutoCommit(connection, originalAutoCommit, failure);
        close(connection, failure);
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

    private static void restoreAfterCommit(
            Connection connection, boolean originalAutoCommit) {
        try {
            restoreAutoCommit(connection, originalAutoCommit);
        } catch (SQLException exception) {
            logAfterCommit(Level.WARNING,
                    "Database transaction committed, but connection state could not be restored",
                    exception);
        }
    }

    private static void close(Connection connection, Throwable failure) {
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static void closeAfterCommit(Connection connection) {
        try {
            connection.close();
        } catch (SQLException exception) {
            logAfterCommit(Level.WARNING,
                    "Database transaction committed, but its connection could not be closed",
                    exception);
        }
    }

    private static void logAfterCommit(Level level, String message, SQLException exception) {
        try {
            LOGGER.log(level, message, exception);
        } catch (RuntimeException loggingFailure) {
            exception.addSuppressed(loggingFailure);
        }
    }

    private static ServiceException.StorageFailure storageFailure(SQLException exception) {
        return new ServiceException.StorageFailure(
                "The database transaction could not be completed", exception);
    }
}
