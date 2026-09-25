package evidencelogger.infrastructure.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import evidencelogger.service.ServiceException;

/** Applies and verifies ordered, immutable SQL migrations. */
public final class MigrationRunner {
    private static final String MIGRATION_LIST = "/db/migration/migrations.list";
    private static final Pattern MIGRATION_NAME =
            Pattern.compile("V(\\d{3,})__([a-z0-9_]+)\\.sql");

    private final ConnectionFactory connectionFactory;
    private final Clock clock;

    /** Creates a migration runner for the configured database. */
    public MigrationRunner(ConnectionFactory connectionFactory, Clock clock) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Applies pending migrations and verifies previously applied checksums. */
    public void migrate() {
        List<Migration> migrations = loadMigrations();
        try (Connection connection = connectionFactory.open()) {
            createMetadataTable(connection);
            Map<Integer, AppliedMigration> applied = loadAppliedMigrations(connection);
            verifyHistory(migrations, applied);
            for (Migration migration : migrations) {
                if (!applied.containsKey(migration.version())) {
                    apply(connection, migration);
                }
            }
        } catch (SQLException exception) {
            throw new ServiceException.StorageFailure(
                    "The database schema could not be prepared", exception);
        }
    }

    private List<Migration> loadMigrations() {
        String listContent = readResource(MIGRATION_LIST);
        List<Migration> migrations = new ArrayList<>();
        int previousVersion = 0;
        for (String line : listContent.lines().toList()) {
            String resourceName = line.trim();
            if (resourceName.isEmpty() || resourceName.startsWith("#")) {
                continue;
            }
            Matcher matcher = MIGRATION_NAME.matcher(resourceName);
            if (!matcher.matches()) {
                throw incompatible("Invalid migration resource name: " + resourceName);
            }
            int version = Integer.parseInt(matcher.group(1));
            if (version <= previousVersion) {
                throw incompatible("Migrations are not strictly ordered");
            }
            String script = readResource("/db/migration/" + resourceName);
            migrations.add(new Migration(version, resourceName, checksum(script), script));
            previousVersion = version;
        }
        if (migrations.isEmpty()) {
            throw incompatible("No database migrations were found");
        }
        return List.copyOf(migrations);
    }

    private String readResource(String resourcePath) {
        try (InputStream stream = MigrationRunner.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw incompatible("Missing migration resource: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ServiceException.StorageFailure(
                    "A database migration resource could not be read", exception);
        }
    }

    private static String checksum(String script) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonicalScript = script.replace("\r\n", "\n").replace('\r', '\n');
            return HexFormat.of().formatHex(
                    digest.digest(canonicalScript.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void createMetadataTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS schema_migration (
                        version INTEGER PRIMARY KEY,
                        name TEXT NOT NULL UNIQUE,
                        checksum TEXT NOT NULL,
                        applied_at TEXT NOT NULL
                    )
                    """);
        }
    }

    private static Map<Integer, AppliedMigration> loadAppliedMigrations(Connection connection)
            throws SQLException {
        Map<Integer, AppliedMigration> applied = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version, name, checksum FROM schema_migration ORDER BY version");
                ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                int version = results.getInt("version");
                applied.put(version, new AppliedMigration(
                        results.getString("name"), results.getString("checksum")));
            }
        }
        return applied;
    }

    private static void verifyHistory(
            List<Migration> migrations, Map<Integer, AppliedMigration> applied) {
        int latestSupported = migrations.getLast().version();
        for (Map.Entry<Integer, AppliedMigration> entry : applied.entrySet()) {
            int version = entry.getKey();
            if (version > latestSupported) {
                throw incompatible("Database schema is newer than this application");
            }
            Migration expected = migrations.stream()
                    .filter(migration -> migration.version() == version)
                    .findFirst()
                    .orElseThrow(() -> incompatible("Database migration history has a gap"));
            AppliedMigration actual = entry.getValue();
            if (!expected.name().equals(actual.name())
                    || !expected.checksum().equals(actual.checksum())) {
                throw incompatible("Database migration checksum verification failed");
            }
        }
        boolean missingPredecessor = false;
        for (Migration migration : migrations) {
            if (!applied.containsKey(migration.version())) {
                missingPredecessor = true;
            } else if (missingPredecessor) {
                throw incompatible("Database migration history has a gap");
            }
        }
    }

    private void apply(Connection connection, Migration migration) throws SQLException {
        boolean originalAutoCommit = connection.getAutoCommit();
        boolean originalForeignKeys = foreignKeysEnabled(connection);
        if (originalForeignKeys) {
            setForeignKeys(connection, false);
        }
        connection.setAutoCommit(false);
        try {
            for (String sql : splitStatements(migration.script())) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(sql);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO schema_migration(version, name, checksum, applied_at)
                    VALUES (?, ?, ?, ?)
                    """)) {
                statement.setInt(1, migration.version());
                statement.setString(2, migration.name());
                statement.setString(3, migration.checksum());
                statement.setString(4, Instant.now(clock).toString());
                statement.executeUpdate();
            }
            verifyForeignKeys(connection);
            connection.commit();
        } catch (SQLException failure) {
            rollback(connection, failure);
            throw failure;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
            if (originalForeignKeys) {
                setForeignKeys(connection, true);
            }
        }
    }

    private static boolean foreignKeysEnabled(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery("PRAGMA foreign_keys")) {
            return results.getBoolean(1);
        }
    }

    private static void setForeignKeys(Connection connection, boolean enabled) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = " + (enabled ? "ON" : "OFF"));
        }
    }

    private static void verifyForeignKeys(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (results.next()) {
                throw new SQLException("Database migration introduced a foreign-key violation");
            }
        }
    }

    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        for (int index = 0; index < script.length(); index++) {
            char character = script.charAt(index);
            if (character == '\'' && inString && index + 1 < script.length()
                    && script.charAt(index + 1) == '\'') {
                current.append(character).append(character);
                index++;
            } else if (character == '\'') {
                inString = !inString;
                current.append(character);
            } else if (character == ';' && !inString) {
                addStatement(statements, current);
            } else {
                current.append(character);
            }
        }
        addStatement(statements, current);
        return statements;
    }

    private static void addStatement(List<String> statements, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
        current.setLength(0);
    }

    private static void rollback(Connection connection, SQLException failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static ServiceException.StorageFailure incompatible(String message) {
        return new ServiceException.StorageFailure(message, new IllegalStateException(message));
    }

    private record Migration(int version, String name, String checksum, String script) {
    }

    private record AppliedMigration(String name, String checksum) {
    }
}
