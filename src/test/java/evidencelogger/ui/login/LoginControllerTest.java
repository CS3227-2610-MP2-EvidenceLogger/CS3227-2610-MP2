package evidencelogger.ui.login;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.repository.UserAccountCredentials;
import evidencelogger.repository.UserAccountRepository;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticationService;
import evidencelogger.service.auth.SessionManager;

class LoginControllerTest {
    private static final UserId USER_ID = UserId.parse(
            "00000000-0000-0000-0000-000000000301");
    private static final byte[] SALT = {1, 2, 3};
    private static final byte[] HASH = {4, 5, 6};

    private SessionManager sessions;
    private LoginController controller;

    @BeforeEach
    void setUp() {
        sessions = new SessionManager();
        UserAccountRepository accounts = username -> "custodian".equals(username)
                ? Optional.of(new UserAccountCredentials(
                        USER_ID,
                        "Morgan Custodian",
                        Role.EVIDENCE_CUSTODIAN,
                        "test",
                        1,
                        SALT,
                        HASH))
                : Optional.empty();
        controller = new LoginController(new AuthenticationService(
                accounts, (password, algorithm, iterations, salt, hash) ->
                        new String(password).equals("correct password"),
                sessions));
    }

    @Test
    void successfulSignInReturnsSessionAndInstallsIt() {
        char[] password = "correct password".toCharArray();

        LoginController.Result result = controller.signIn(" custodian ", password);

        assertTrue(result.successful());
        assertEquals(USER_ID, result.session().userId());
        assertEquals(Role.EVIDENCE_CUSTODIAN, result.session().role());
        assertEquals(result.session(), sessions.requireSession());
        assertEquals("", result.message());
        assertArrayEquals(new char[password.length], password);
    }

    @Test
    void invalidCredentialsHaveOneReadableMessageAndNoSession() {
        char[] wrongPassword = "wrong password".toCharArray();

        LoginController.Result wrong = controller.signIn("custodian", wrongPassword);
        LoginController.Result missing = controller.signIn(
                "missing", "anything".toCharArray());

        assertFalse(wrong.successful());
        assertNull(wrong.session());
        assertEquals("Invalid username or password", wrong.message());
        assertEquals(wrong.message(), missing.message());
        assertFalse(sessions.currentSession().isPresent());
        assertArrayEquals(new char[wrongPassword.length], wrongPassword);
    }

    @Test
    void signOutClearsTheAuthenticatedSession() {
        controller.signIn("custodian", "correct password".toCharArray());

        controller.signOut();

        assertFalse(sessions.currentSession().isPresent());
    }

    @Test
    void storageFailureIsLoggedWithTheSameDiagnosticReferenceShownToTheUser() {
        ServiceException.StorageFailure failure = new ServiceException.StorageFailure(
                "Account data could not be read", new IllegalStateException("database unavailable"));
        LoginController failingController = new LoginController(new AuthenticationService(
                username -> {
                    throw failure;
                }, (password, algorithm, iterations, salt, hash) -> false,
                sessions));
        CapturingHandler handler = new CapturingHandler();
        Logger logger = Logger.getLogger(LoginController.class.getName());
        logger.addHandler(handler);
        try {
            LoginController.Result result = failingController.signIn(
                    "custodian", "not logged".toCharArray());

            assertFalse(result.successful());
            assertTrue(result.message().startsWith(
                    "Account data could not be read. Reference: "));
            assertEquals(failure, handler.record.getThrown());
            String reference = result.message().substring(result.message().indexOf("Reference: "));
            assertTrue(handler.record.getMessage().contains(
                    reference.replace("Reference: ", "operationId=")));
            assertFalse(handler.record.getMessage().contains("custodian"));
        } finally {
            logger.removeHandler(handler);
        }
    }

    private static final class CapturingHandler extends Handler {
        private LogRecord record;

        @Override
        public void publish(LogRecord logRecord) {
            record = logRecord;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
