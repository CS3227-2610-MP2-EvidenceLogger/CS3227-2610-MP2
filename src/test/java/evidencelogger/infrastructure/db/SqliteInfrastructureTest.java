package evidencelogger.infrastructure.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import evidencelogger.service.ServiceException;

class SqliteInfrastructureTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    private ConnectionFactory connectionFactory;
    private MigrationRunner migrationRunner;

    @BeforeEach
    void createInfrastructure() {
        connectionFactory = new SqliteConnectionFactory(temporaryDirectory.resolve("evidence.db"));
        migrationRunner = new MigrationRunner(connectionFactory, FIXED_CLOCK);
    }

    @Test
    void everyConnectionEnablesForeignKeysAndABoundedBusyTimeout() throws SQLException {
        try (Connection connection = connectionFactory.open();
                Statement statement = connection.createStatement()) {
            assertEquals(1, pragmaValue(statement, "foreign_keys"));
            assertEquals(5000, pragmaValue(statement, "busy_timeout"));
        }
    }

    @Test
    void emptyDatabaseMigratesAndSeedsThreeHashedDemoAccounts() throws SQLException {
        migrationRunner.migrate();
        migrationRunner.migrate();

        try (Connection connection = connectionFactory.open();
                Statement statement = connection.createStatement()) {
            assertEquals(1, count(statement, "schema_migration"));
            assertEquals(3, count(statement, "user_account"));
            assertEquals(1, countWhere(statement, "user_account", "role = 'EVIDENCE_CUSTODIAN'"));
            assertEquals(2, countWhere(statement, "user_account", "role = 'INVESTIGATOR'"));
            assertEquals(3, countWhere(statement, "user_account", "typeof(password_hash) = 'blob'"));
            assertEquals(3, countWhere(statement, "user_account", "length(password_salt) = 16"));
            assertEquals(3, distinctCount(statement, "user_account", "hex(password_salt)"));
        }
    }

    @Test
    void migrationRejectsChangedChecksumAndNewerSchema() throws SQLException {
        migrationRunner.migrate();

        try (Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE schema_migration SET checksum = ? WHERE version = 1")) {
            statement.setString(1, "changed");
            statement.executeUpdate();
        }

        ServiceException.StorageFailure checksumFailure = assertThrows(
                ServiceException.StorageFailure.class, migrationRunner::migrate);
        assertTrue(checksumFailure.getMessage().contains("checksum"));

        try (Connection connection = connectionFactory.open();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM schema_migration WHERE version = 1");
            statement.executeUpdate("""
                    INSERT INTO schema_migration(version, name, checksum, applied_at)
                    VALUES (999, 'V999__future.sql', 'future', '2026-09-24T12:00:00Z')
                    """);
        }

        ServiceException.StorageFailure newerFailure = assertThrows(
                ServiceException.StorageFailure.class, migrationRunner::migrate);
        assertTrue(newerFailure.getMessage().contains("newer"));
    }

    @Test
    void transactionRunnerCommitsSuccessAndRollsBackFailure() throws SQLException {
        migrationRunner.migrate();
        TransactionRunner runner = new JdbcTransactionRunner(connectionFactory);

        runner.inTransaction(connection -> {
            insertLocation(connection, "00000000-0000-0000-0000-000000000100", "Locker A");
            return null;
        });

        assertThrows(IllegalStateException.class, () -> runner.inTransaction(connection -> {
            insertLocation(connection, "00000000-0000-0000-0000-000000000101", "Locker B");
            throw new IllegalStateException("injected failure");
        }));

        try (Connection connection = connectionFactory.open();
                Statement statement = connection.createStatement()) {
            assertEquals(1, count(statement, "storage_location"));
            assertTrue(exists(statement, "storage_location", "name = 'Locker A'"));
            assertFalse(exists(statement, "storage_location", "name = 'Locker B'"));
        }
    }

    private static int pragmaValue(Statement statement, String name) throws SQLException {
        try (ResultSet results = statement.executeQuery("PRAGMA " + name)) {
            return results.getInt(1);
        }
    }

    private static int count(Statement statement, String table) throws SQLException {
        try (ResultSet results = statement.executeQuery("SELECT count(*) FROM " + table)) {
            return results.getInt(1);
        }
    }

    private static int countWhere(Statement statement, String table, String condition)
            throws SQLException {
        try (ResultSet results = statement.executeQuery(
                "SELECT count(*) FROM " + table + " WHERE " + condition)) {
            return results.getInt(1);
        }
    }

    private static int distinctCount(Statement statement, String table, String expression)
            throws SQLException {
        try (ResultSet results = statement.executeQuery(
                "SELECT count(DISTINCT " + expression + ") FROM " + table)) {
            return results.getInt(1);
        }
    }

    private static boolean exists(Statement statement, String table, String condition)
            throws SQLException {
        try (ResultSet results = statement.executeQuery(
                "SELECT EXISTS(SELECT 1 FROM " + table + " WHERE " + condition + ")")) {
            return results.getBoolean(1);
        }
    }

    private static void insertLocation(Connection connection, String id, String name) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO storage_location(id, name, created_at) VALUES (?, ?, ?)
                """)) {
            statement.setString(1, id);
            statement.setString(2, name);
            statement.setString(3, "2026-09-24T12:00:00Z");
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new ServiceException.StorageFailure("Unable to insert test location", exception);
        }
    }
}
