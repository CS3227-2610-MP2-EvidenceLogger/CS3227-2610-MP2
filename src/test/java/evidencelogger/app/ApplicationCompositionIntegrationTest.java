package evidencelogger.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import evidencelogger.domain.Role;
import evidencelogger.infrastructure.db.ConnectionFactory;
import evidencelogger.infrastructure.db.SqliteConnectionFactory;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;

class ApplicationCompositionIntegrationTest {
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-24T16:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    void startupMigratesBeforeReturningOneSharedServiceGraphAndCloseClearsSession()
            throws SQLException, GeneralSecurityException {
        Path database = temporaryDirectory.resolve("composition.db");
        ApplicationComposition composition = ApplicationComposition.start(database, FIXED_CLOCK);
        insertTestAccount(composition.connectionFactory());

        char[] password = "composition test".toCharArray();
        AuthenticatedSession session = composition.authentication().signIn("composition.user", password);

        assertEquals(session, composition.sessions().requireSession());
        assertNotNull(composition.transactions());
        assertNotNull(composition.authorization());
        assertNotNull(composition.auditEvents());
        assertNotNull(composition.caseworkCommands());
        assertNotNull(composition.caseworkQueries());
        assertEquals(List.of(), composition.checkoutQueries().listRequests());
        try (Connection connection = composition.connectionFactory().open();
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery("SELECT count(*) FROM schema_migration")) {
            assertEquals(2, results.getInt(1));
        }

        composition.close();
        assertFalse(composition.sessions().currentSession().isPresent());
    }

    @Test
    void startupStopsWhenDatabaseSchemaIsNewerThanApplication() throws SQLException {
        Path database = temporaryDirectory.resolve("future.db");
        ConnectionFactory connectionFactory = new SqliteConnectionFactory(database);
        try (Connection connection = connectionFactory.open();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE schema_migration(
                        version INTEGER PRIMARY KEY,
                        name TEXT NOT NULL UNIQUE,
                        checksum TEXT NOT NULL,
                        applied_at TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO schema_migration(version, name, checksum, applied_at)
                    VALUES (999, 'V999__future.sql', 'future', '2026-09-24T16:00:00Z')
                    """);
        }

        assertThrows(ServiceException.StorageFailure.class, () ->
                ApplicationComposition.start(database, FIXED_CLOCK));
    }

    @Test
    void documentedDemoCredentialsAuthenticateAgainstTheSeededDatabase() throws IOException {
        List<DemoAccount> accounts = readDocumentedDemoAccounts();

        try (ApplicationComposition composition = ApplicationComposition.start(
                temporaryDirectory.resolve("demo-accounts.db"), FIXED_CLOCK)) {
            assertEquals(3, accounts.size());
            for (DemoAccount account : accounts) {
                AuthenticatedSession session = composition.authentication().signIn(
                        account.username(), account.password().toCharArray());
                assertEquals(account.role(), session.role());
            }
        }
    }

    private static void insertTestAccount(ConnectionFactory connectionFactory)
            throws SQLException, GeneralSecurityException {
        byte[] salt = new byte[16];
        for (int index = 0; index < salt.length; index++) {
            salt[index] = (byte) (32 + index);
        }
        PBEKeySpec spec = new PBEKeySpec("composition test".toCharArray(), salt, 1000, 256);
        byte[] hash;
        try {
            hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
        try (Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO user_account(
                            id, username, display_name, role, password_algorithm,
                            password_iterations, password_salt, password_hash
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            statement.setString(1, "00000000-0000-0000-0000-000000000400");
            statement.setString(2, "composition.user");
            statement.setString(3, "Composition User");
            statement.setString(4, Role.INVESTIGATOR.name());
            statement.setString(5, "PBKDF2WithHmacSHA256");
            statement.setInt(6, 1000);
            statement.setBytes(7, salt);
            statement.setBytes(8, hash);
            statement.executeUpdate();
        }
    }

    private static List<DemoAccount> readDocumentedDemoAccounts() throws IOException {
        List<DemoAccount> accounts = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("README.md"))) {
            if (!line.startsWith("| Evidence Custodian |")
                    && !line.startsWith("| Investigator |")) {
                continue;
            }
            String[] cells = line.split("\\|");
            Role role = cells[1].trim().equals("Evidence Custodian")
                    ? Role.EVIDENCE_CUSTODIAN
                    : Role.INVESTIGATOR;
            accounts.add(new DemoAccount(
                    removeCodeFormatting(cells[2]),
                    removeCodeFormatting(cells[3]),
                    role));
        }
        return List.copyOf(accounts);
    }

    private static String removeCodeFormatting(String cell) {
        return cell.trim().replace("`", "");
    }

    private record DemoAccount(String username, String password, Role role) {
    }
}
