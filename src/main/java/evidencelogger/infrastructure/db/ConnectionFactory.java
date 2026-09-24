package evidencelogger.infrastructure.db;

import java.sql.Connection;
import java.sql.SQLException;

/** Opens configured database connections owned by the caller. */
@FunctionalInterface
public interface ConnectionFactory {
    /**
     * Opens a new configured connection.
     *
     * @return a connection that the caller must close
     * @throws SQLException when the connection cannot be opened or configured
     */
    Connection open() throws SQLException;
}
