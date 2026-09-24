package evidencelogger.infrastructure.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MigrationUpgradeIntegrationTest {
    private static final String V1_RESOURCE =
            "/db/migration/V001__initial_schema.sql";
    private static final String EXISTING_EVIDENCE_ID =
            "00000000-0000-0000-0000-000000000600";

    @TempDir
    Path temporaryDirectory;

    @Test
    void versionOneDatabaseUpgradesWithoutEditingTheReleasedMigration() throws SQLException {
        ConnectionFactory connections = new SqliteConnectionFactory(
                temporaryDirectory.resolve("upgrade.db"));
        try (Connection connection = connections.open()) {
            installVersionOne(connection);
            insertVersionOneEvidence(connection);
        }

        new MigrationRunner(connections, Clock.systemUTC()).migrate();

        try (Connection connection = connections.open();
                Statement statement = connection.createStatement()) {
            assertEquals(2, scalar(statement, "SELECT count(*) FROM schema_migration"));
            assertEquals(1, scalar(statement, "SELECT count(*) FROM evidence_item"
                    + " WHERE id = '" + EXISTING_EVIDENCE_ID + "'"
                    + " AND custody_state = 'CHECKED_OUT'"));
            assertEquals(1, scalar(statement, "SELECT count(*) FROM checkout"
                    + " WHERE id = '00000000-0000-0000-0000-000000000622'"));
            assertEquals(1, scalar(statement, "SELECT count(*) FROM audit_event"
                    + " WHERE id = '00000000-0000-0000-0000-000000000623'"));
            assertEquals(0, scalar(statement, "SELECT count(*) FROM pragma_foreign_key_check"));
            statement.executeUpdate("""
                    INSERT INTO evidence_item(
                        id, case_id, public_reference, description,
                        storage_location_id, custody_state, registered_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000601',
                        '00000000-0000-0000-0000-000000000610',
                        'EV-HANDIN', 'Awaiting return inspection',
                        '00000000-0000-0000-0000-000000000611',
                        'HANDIN_AWAITING_ACK', '2026-09-24T00:00:00Z'
                    )
                    """);
            assertThrows(SQLException.class, () -> statement.executeUpdate("""
                    INSERT INTO evidence_item(
                        id, case_id, public_reference, description,
                        storage_location_id, custody_state, registered_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000602',
                        '00000000-0000-0000-0000-000000000610',
                        'EV-HELD', 'Obsolete hold state',
                        '00000000-0000-0000-0000-000000000611',
                        'HELD_FOR_REVIEW', '2026-09-24T00:00:00Z'
                    )
                    """));
        }
    }

    @Test
    void versionOneUpgradeClearlyRejectsHeldEvidenceState() throws SQLException {
        ConnectionFactory connections = new SqliteConnectionFactory(
                temporaryDirectory.resolve("held-evidence.db"));
        try (Connection connection = connections.open()) {
            installVersionOne(connection);
            insertVersionOneHeldEvidence(connection);
        }

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                new MigrationRunner(connections, Clock.systemUTC()).migrate());

        assertTrue(hasMessage(failure, "v002_unsupported_held_evidence_state"));
        assertVersionOneRemainsApplied(connections);
    }

    @Test
    void versionOneUpgradeClearlyRejectsHeldCheckoutOutcome() throws SQLException {
        ConnectionFactory connections = new SqliteConnectionFactory(
                temporaryDirectory.resolve("held-checkout.db"));
        try (Connection connection = connections.open()) {
            installVersionOne(connection);
            insertVersionOneEvidence(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        UPDATE checkout SET completed_at = '2026-09-24T00:00:00Z',
                            return_outcome = 'HELD_FOR_REVIEW'
                        """);
            }
        }

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                new MigrationRunner(connections, Clock.systemUTC()).migrate());

        assertTrue(hasMessage(failure, "v002_unsupported_held_checkout_outcome"));
        assertVersionOneRemainsApplied(connections);
    }

    @Test
    void versionOneUpgradeClearlyRejectsReversalWithoutReason() throws SQLException {
        ConnectionFactory connections = new SqliteConnectionFactory(
                temporaryDirectory.resolve("legacy-reversal.db"));
        try (Connection connection = connections.open()) {
            installVersionOne(connection);
            insertVersionOneEvidence(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        UPDATE handoff SET acknowledged_at = NULL,
                            reversed_at = '2026-09-23T01:04:00Z'
                        """);
            }
        }

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                new MigrationRunner(connections, Clock.systemUTC()).migrate());

        assertTrue(hasMessage(failure, "v002_reversed_handoff_requires_reason"));
        assertVersionOneRemainsApplied(connections);
    }

    private static void installVersionOne(Connection connection) throws SQLException {
        String script = readResource(V1_RESOURCE);
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                for (String sql : script.split(";")) {
                    if (!sql.isBlank()) {
                        statement.executeUpdate(sql.trim());
                    }
                }
                statement.executeUpdate("""
                        CREATE TABLE schema_migration (
                            version INTEGER PRIMARY KEY,
                            name TEXT NOT NULL UNIQUE,
                            checksum TEXT NOT NULL,
                            applied_at TEXT NOT NULL
                        )
                        """);
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO schema_migration(version, name, checksum, applied_at)
                    VALUES (1, 'V001__initial_schema.sql', ?, '2026-09-24T00:00:00Z')
                    """)) {
                statement.setString(1, checksum(script));
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static void insertVersionOneEvidence(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO case_record(id, title, created_at) VALUES (
                        '00000000-0000-0000-0000-000000000610',
                        'Migration case', '2026-09-24T00:00:00Z'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO storage_location(id, name, created_at) VALUES (
                        '00000000-0000-0000-0000-000000000611',
                        'Migration locker', '2026-09-24T00:00:00Z'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO evidence_item(
                        id, case_id, public_reference, description,
                        storage_location_id, custody_state, registered_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000600',
                        '00000000-0000-0000-0000-000000000610',
                        'EV-EXISTING', 'Existing evidence',
                        '00000000-0000-0000-0000-000000000611',
                        'CHECKED_OUT', '2026-09-24T00:00:00Z'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO checkout_request(
                        id, evidence_id, requester_id, purpose, expected_return_at,
                        status, requested_at, decided_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000620',
                        '00000000-0000-0000-0000-000000000600',
                        '00000000-0000-0000-0000-000000000002',
                        'Existing request', '2026-09-25T00:00:00Z',
                        'CONSUMED', '2026-09-23T00:00:00Z', '2026-09-23T01:00:00Z'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO handoff(
                        id, request_id, evidence_id, custodian_id,
                        recorded_at, acknowledged_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000621',
                        '00000000-0000-0000-0000-000000000620',
                        '00000000-0000-0000-0000-000000000600',
                        '00000000-0000-0000-0000-000000000001',
                        '2026-09-23T01:00:00Z', '2026-09-23T01:05:00Z'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO checkout(
                        id, handoff_id, request_id, evidence_id,
                        collector_id, collected_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000622',
                        '00000000-0000-0000-0000-000000000621',
                        '00000000-0000-0000-0000-000000000620',
                        '00000000-0000-0000-0000-000000000600',
                        '00000000-0000-0000-0000-000000000002',
                        '2026-09-23T01:05:00Z'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO audit_event(
                        id, event_type, actor_id, actor_role, event_time,
                        case_id, evidence_id, request_id, handoff_id, checkout_id
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000623',
                        'COLLECTION_ACKNOWLEDGED',
                        '00000000-0000-0000-0000-000000000002',
                        'INVESTIGATOR', '2026-09-23T01:05:00Z',
                        '00000000-0000-0000-0000-000000000610',
                        '00000000-0000-0000-0000-000000000600',
                        '00000000-0000-0000-0000-000000000620',
                        '00000000-0000-0000-0000-000000000621',
                        '00000000-0000-0000-0000-000000000622'
                    )
                    """);
        }
    }

    private static void insertVersionOneHeldEvidence(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO case_record(id, title, created_at) VALUES (
                        '00000000-0000-0000-0000-000000000610',
                        'Migration case', '2026-09-24T00:00:00Z'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO storage_location(id, name, created_at) VALUES (
                        '00000000-0000-0000-0000-000000000611',
                        'Migration locker', '2026-09-24T00:00:00Z'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO evidence_item(
                        id, case_id, public_reference, description,
                        storage_location_id, custody_state, registered_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000600',
                        '00000000-0000-0000-0000-000000000610',
                        'EV-HELD', 'Unsupported held evidence',
                        '00000000-0000-0000-0000-000000000611',
                        'HELD_FOR_REVIEW', '2026-09-24T00:00:00Z'
                    )
                    """);
        }
    }

    private static void assertVersionOneRemainsApplied(ConnectionFactory connections)
            throws SQLException {
        try (Connection connection = connections.open();
                Statement statement = connection.createStatement()) {
            assertEquals(1, scalar(statement, "SELECT count(*) FROM schema_migration"));
            assertEquals(1, scalar(statement,
                    "SELECT count(*) FROM schema_migration WHERE version = 1"));
        }
    }

    private static boolean hasMessage(Throwable failure, String expectedText) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (String.valueOf(current.getMessage()).contains(expectedText)) {
                return true;
            }
        }
        return false;
    }

    private static int scalar(Statement statement, String sql) throws SQLException {
        try (ResultSet results = statement.executeQuery(sql)) {
            return results.getInt(1);
        }
    }

    private static String readResource(String resourcePath) {
        try (InputStream stream = MigrationUpgradeIntegrationTest.class
                .getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing resource " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + resourcePath, exception);
        }
    }

    private static String checksum(String script) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(script.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
