package evidencelogger.repository.jdbc;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;

import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.ConnectionFactory;
import evidencelogger.infrastructure.db.JdbcTransactionRunner;
import evidencelogger.infrastructure.db.MigrationRunner;
import evidencelogger.infrastructure.db.SqliteConnectionFactory;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.repository.RepositoryException;

/** Production-schema fixture shared by checkout JDBC integration tests. */
final class CheckoutRepositoryTestDatabase {
    static final UserId CUSTODIAN_ID =
            UserId.parse("00000000-0000-0000-0000-000000000001");
    static final UserId INVESTIGATOR_ID =
            UserId.parse("00000000-0000-0000-0000-000000000002");

    private static final String CASE_ID = "00000000-0000-0000-0000-000000000500";
    private static final String LOCATION_ID = "00000000-0000-0000-0000-000000000501";
    private static final String FIXTURE_TIME = "2026-09-23T00:00:00Z";

    private final ConnectionFactory connections;
    private final TransactionRunner transactions;

    CheckoutRepositoryTestDatabase(Path databasePath) {
        connections = new SqliteConnectionFactory(databasePath);
        new MigrationRunner(connections, Clock.systemUTC()).migrate();
        transactions = new JdbcTransactionRunner(connections);
    }

    <T> T inTransaction(TransactionRunner.TransactionalWork<T> work) {
        return transactions.inTransaction(work);
    }

    boolean inTransactionBoolean(TransactionRunner.TransactionalWork<Boolean> work) {
        return transactions.inTransaction(work);
    }

    void insertEvidence(EvidenceId evidenceId) {
        inTransaction(connection -> {
            execute(connection, """
                    INSERT INTO case_record(id, title, created_at) VALUES (?, ?, ?)
                    """, CASE_ID, "Checkout integration case", FIXTURE_TIME);
            execute(connection, """
                    INSERT INTO storage_location(id, name, created_at) VALUES (?, ?, ?)
                    """, LOCATION_ID, "Checkout integration locker", FIXTURE_TIME);
            execute(connection, """
                    INSERT INTO evidence_item(
                        id, case_id, public_reference, description,
                        storage_location_id, custody_state, registered_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    evidenceId.toString(),
                    CASE_ID,
                    "EV-" + evidenceId,
                    "Checkout integration evidence",
                    LOCATION_ID,
                    EvidenceCustodyState.IN_STORAGE.name(),
                    FIXTURE_TIME);
            return null;
        });
    }

    void insertAdditionalEvidence(EvidenceId evidenceId) {
        inTransaction(connection -> {
            execute(connection, """
                    INSERT INTO evidence_item(
                        id, case_id, public_reference, description,
                        storage_location_id, custody_state, registered_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    evidenceId.toString(),
                    CASE_ID,
                    "EV-" + evidenceId,
                    "Additional checkout integration evidence",
                    LOCATION_ID,
                    EvidenceCustodyState.IN_STORAGE.name(),
                    FIXTURE_TIME);
            return null;
        });
    }

    private static void execute(Connection connection, String sql, String... values) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RepositoryException.StorageFailure(
                    "Unable to create checkout test fixture", exception);
        }
    }
}
