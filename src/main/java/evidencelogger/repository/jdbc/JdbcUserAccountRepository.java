package evidencelogger.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.ConnectionFactory;
import evidencelogger.repository.UserAccountCredentials;
import evidencelogger.repository.UserAccountRepository;
import evidencelogger.service.ServiceException;

/** JDBC credential lookup that never exposes credentials beyond the auth service. */
public final class JdbcUserAccountRepository implements UserAccountRepository {
    private final ConnectionFactory connectionFactory;

    /** Creates a credential repository using short-lived read connections. */
    public JdbcUserAccountRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory");
    }

    @Override
    public Optional<UserAccountCredentials> findByUsername(String username) {
        Objects.requireNonNull(username, "username");
        try (Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT id, display_name, role, password_algorithm,
                               password_iterations, password_salt, password_hash
                        FROM user_account
                        WHERE username = ? COLLATE NOCASE
                        """)) {
            statement.setString(1, username);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return Optional.of(new UserAccountCredentials(
                        UserId.parse(results.getString("id")),
                        results.getString("display_name"),
                        Role.valueOf(results.getString("role")),
                        results.getString("password_algorithm"),
                        results.getInt("password_iterations"),
                        results.getBytes("password_salt"),
                        results.getBytes("password_hash")));
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new ServiceException.StorageFailure(
                    "Account data could not be read", exception);
        }
    }
}
