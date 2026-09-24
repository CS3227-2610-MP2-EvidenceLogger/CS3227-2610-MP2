package evidencelogger.infrastructure.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Objects;

/** Opens SQLite connections with the required per-connection pragmas. */
public final class SqliteConnectionFactory implements ConnectionFactory {
    private static final Duration DEFAULT_BUSY_TIMEOUT = Duration.ofSeconds(5);

    private final Path databasePath;
    private final int busyTimeoutMillis;

    /** Creates a factory using the default bounded busy timeout. */
    public SqliteConnectionFactory(Path databasePath) {
        this(databasePath, DEFAULT_BUSY_TIMEOUT);
    }

    /** Creates a factory for the database path and busy timeout. */
    public SqliteConnectionFactory(Path databasePath, Duration busyTimeout) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath")
                .toAbsolutePath().normalize();
        Objects.requireNonNull(busyTimeout, "busyTimeout");
        long timeoutMillis = busyTimeout.toMillis();
        if (timeoutMillis < 0 || timeoutMillis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("busyTimeout is outside the supported range");
        }
        busyTimeoutMillis = (int) timeoutMillis;
    }

    @Override
    public Connection open() throws SQLException {
        createParentDirectory();
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try {
            configure(connection);
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    private void createParentDirectory() throws SQLException {
        Path parent = databasePath.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new SQLException("Unable to create the database directory", exception);
        }
    }

    private void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = " + busyTimeoutMillis);
        }
    }
}
