package evidencelogger.infrastructure.db;

/**
 * Executes one application command atomically. The callback deliberately
 * exposes no JDBC connection so service contracts remain persistence-neutral.
 */
public interface TransactionRunner {
    <T> T inTransaction(TransactionalWork<T> work);

    @FunctionalInterface
    interface TransactionalWork<T> {
        T execute();
    }
}
