package evidencelogger.infrastructure.db;

import java.sql.Connection;

/**
 * Executes one application command atomically. The runner owns the supplied
 * JDBC connection and its transaction boundary; work must not commit, roll
 * back, or close it.
 *
 * <p>This is an internal infrastructure contract. Public application service
 * contracts remain persistence-neutral.</p>
 */
public interface TransactionRunner {
    <T> T inTransaction(TransactionalWork<T> work);

    /** Work performed using the connection owned by the transaction runner. */
    @FunctionalInterface
    interface TransactionalWork<T> {
        T execute(Connection connection);
    }
}
