package evidencelogger.service.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.ConnectionFactory;
import evidencelogger.infrastructure.db.MigrationRunner;
import evidencelogger.infrastructure.db.SqliteConnectionFactory;
import evidencelogger.infrastructure.security.Pbkdf2PasswordVerifier;
import evidencelogger.repository.jdbc.JdbcAuthorizationRepository;
import evidencelogger.repository.jdbc.JdbcUserAccountRepository;
import evidencelogger.service.ServiceException;

class AuthenticationAuthorizationIntegrationTest {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int TEST_ITERATIONS = 1000;
    private static final UserId CUSTODIAN_ID =
            UserId.parse("00000000-0000-0000-0000-000000000001");
    private static final UserId INVESTIGATOR_ID =
            UserId.parse("00000000-0000-0000-0000-000000000002");
    private static final CaseId CASE_ID =
            CaseId.parse("00000000-0000-0000-0000-000000000200");
    private static final CheckoutId CHECKOUT_ID =
            CheckoutId.parse("00000000-0000-0000-0000-000000000205");

    @TempDir
    Path temporaryDirectory;

    private ConnectionFactory connectionFactory;
    private SessionManager sessions;

    @BeforeEach
    void createDatabase() {
        connectionFactory = new SqliteConnectionFactory(temporaryDirectory.resolve("auth.db"));
        new MigrationRunner(connectionFactory, Clock.systemUTC()).migrate();
        sessions = new SessionManager();
    }

    @Test
    void signInInstallsOneSessionClearsPasswordAndLogoutRemovesIt()
            throws SQLException, GeneralSecurityException {
        char[] correctPassword = "test password".toCharArray();
        insertTestAccount("test.user", correctPassword.clone());
        AuthenticationService authentication = authenticationService();

        AuthenticatedSession session = authentication.signIn("TEST.USER", correctPassword);

        assertEquals("Test User", session.displayName());
        assertEquals(Role.INVESTIGATOR, session.role());
        assertEquals(session, sessions.requireSession());
        assertArrayEquals(new char[correctPassword.length], correctPassword);

        authentication.signOut();
        assertFalse(sessions.currentSession().isPresent());
        assertThrows(ServiceException.Unauthenticated.class, sessions::requireSession);
    }

    @Test
    void invalidCredentialsUseOneMessageClearPasswordAndLeaveNoSession()
            throws SQLException, GeneralSecurityException {
        insertTestAccount("test.user", "correct password".toCharArray());
        AuthenticationService authentication = authenticationService();
        sessions.establish(new AuthenticatedSession(
                CUSTODIAN_ID, Role.EVIDENCE_CUSTODIAN, "Existing Session"));
        char[] wrongPassword = "wrong password".toCharArray();

        ServiceException.Unauthenticated wrong = assertThrows(
                ServiceException.Unauthenticated.class, () ->
                        authentication.signIn("test.user", wrongPassword));
        ServiceException.Unauthenticated missing = assertThrows(
                ServiceException.Unauthenticated.class, () ->
                        authentication.signIn("missing.user", "anything".toCharArray()));

        assertEquals(wrong.getMessage(), missing.getMessage());
        assertArrayEquals(new char[wrongPassword.length], wrongPassword);
        assertFalse(sessions.currentSession().isPresent());
    }

    @Test
    void roleAndCurrentRelationshipChecksRejectDirectUnauthorizedCalls() throws SQLException {
        seedAuthorizationRelationships();
        DefaultAuthorizationService authorization = new DefaultAuthorizationService(
                sessions, new JdbcAuthorizationRepository(connectionFactory));

        sessions.establish(new AuthenticatedSession(
                CUSTODIAN_ID, Role.EVIDENCE_CUSTODIAN, "Morgan Custodian"));
        assertEquals(CUSTODIAN_ID, authorization.requireCustodian().userId());
        assertThrows(ServiceException.Forbidden.class, authorization::requireInvestigator);
        assertThrows(ServiceException.Forbidden.class, () ->
                authorization.requireAssignedInvestigator(CASE_ID));

        sessions.establish(new AuthenticatedSession(
                INVESTIGATOR_ID, Role.INVESTIGATOR, "Alex Investigator"));
        assertEquals(INVESTIGATOR_ID,
                authorization.requireAssignedInvestigator(CASE_ID).userId());
        assertEquals(INVESTIGATOR_ID,
                authorization.requireCollectingInvestigator(CHECKOUT_ID).userId());

        removeAssignment();
        assertThrows(ServiceException.Forbidden.class, () ->
                authorization.requireAssignedInvestigator(CASE_ID));
        assertThrows(ServiceException.Forbidden.class, () ->
                authorization.requireCollectingInvestigator(CHECKOUT_ID));
    }

    @Test
    void connectionBoundAuthorizationUsesTheCallerOwnedTransaction() throws SQLException {
        seedAuthorizationRelationships();
        JdbcAuthorizationRepository authorization =
                new JdbcAuthorizationRepository(connectionFactory);

        try (Connection connection = connectionFactory.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM case_assignment WHERE case_id = ? AND investigator_id = ?
                    """)) {
                statement.setString(1, CASE_ID.toString());
                statement.setString(2, INVESTIGATOR_ID.toString());
                statement.executeUpdate();
            }

            assertFalse(authorization.isAssigned(connection, CASE_ID, INVESTIGATOR_ID));
            assertFalse(authorization.isCollectingInvestigator(
                    connection, CHECKOUT_ID, INVESTIGATOR_ID));
            connection.rollback();
        }
    }

    private AuthenticationService authenticationService() {
        return new AuthenticationService(
                new JdbcUserAccountRepository(connectionFactory),
                new Pbkdf2PasswordVerifier(),
                sessions);
    }

    private void insertTestAccount(String username, char[] password)
            throws SQLException, GeneralSecurityException {
        byte[] salt = new byte[16];
        for (int index = 0; index < salt.length; index++) {
            salt[index] = (byte) (index + 1);
        }
        PBEKeySpec spec = new PBEKeySpec(password, salt, TEST_ITERATIONS, 256);
        byte[] hash;
        try {
            hash = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
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
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, username);
            statement.setString(3, "Test User");
            statement.setString(4, Role.INVESTIGATOR.name());
            statement.setString(5, ALGORITHM);
            statement.setInt(6, TEST_ITERATIONS);
            statement.setBytes(7, salt);
            statement.setBytes(8, hash);
            statement.executeUpdate();
        }
    }

    private void seedAuthorizationRelationships() throws SQLException {
        try (Connection connection = connectionFactory.open();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO case_record(id, title, created_at)
                    VALUES ('00000000-0000-0000-0000-000000000200', 'Demo Case', '2026-09-24T12:00:00Z')
                    """);
            statement.executeUpdate("""
                    INSERT INTO case_assignment(case_id, investigator_id, assigned_at)
                    VALUES (
                        '00000000-0000-0000-0000-000000000200',
                        '00000000-0000-0000-0000-000000000002',
                        '2026-09-24T12:00:00Z'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO storage_location(id, name, created_at)
                    VALUES ('00000000-0000-0000-0000-000000000201', 'Locker A', '2026-09-24T12:00:00Z')
                    """);
            statement.executeUpdate("""
                    INSERT INTO evidence_item(
                        id, case_id, public_reference, description,
                        storage_location_id, custody_state, registered_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000202',
                        '00000000-0000-0000-0000-000000000200',
                        'EV-001', 'Demo evidence',
                        '00000000-0000-0000-0000-000000000201',
                        'CHECKED_OUT', '2026-09-24T12:00:00Z'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO checkout_request(
                        id, evidence_id, requester_id, purpose,
                        expected_return_at, status, requested_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000203',
                        '00000000-0000-0000-0000-000000000202',
                        '00000000-0000-0000-0000-000000000002',
                        'Review', '2026-09-25T12:00:00Z', 'CONSUMED', '2026-09-24T12:00:00Z'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO handoff(
                        id, request_id, evidence_id, custodian_id, recorded_at, acknowledged_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000204',
                        '00000000-0000-0000-0000-000000000203',
                        '00000000-0000-0000-0000-000000000202',
                        '00000000-0000-0000-0000-000000000001',
                        '2026-09-24T12:00:00Z', '2026-09-24T12:01:00Z'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO checkout(
                        id, handoff_id, request_id, evidence_id, collector_id, collected_at
                    ) VALUES (
                        '00000000-0000-0000-0000-000000000205',
                        '00000000-0000-0000-0000-000000000204',
                        '00000000-0000-0000-0000-000000000203',
                        '00000000-0000-0000-0000-000000000202',
                        '00000000-0000-0000-0000-000000000002',
                        '2026-09-24T12:01:00Z'
                    )
                    """);
        }
    }

    private void removeAssignment() throws SQLException {
        try (Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement("""
                        DELETE FROM case_assignment WHERE case_id = ? AND investigator_id = ?
                        """)) {
            statement.setString(1, CASE_ID.toString());
            statement.setString(2, INVESTIGATOR_ID.toString());
            statement.executeUpdate();
        }
    }
}
