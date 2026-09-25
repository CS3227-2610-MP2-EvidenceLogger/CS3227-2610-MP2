package evidencelogger.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.repository.UserAccountCredentials;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.AuthenticationService;
import evidencelogger.service.auth.SessionManager;

class AuthenticatedRoleRouterTest {
    private static final byte[] SALT = {1};
    private static final byte[] HASH = {2};

    private SessionManager sessions;
    private AuthenticationService authentication;
    private AtomicReference<AuthenticatedSession> custodianRoute;
    private AtomicReference<String> loginRoute;
    private EvidenceLoggerApplication.AuthenticatedRoleRouter router;

    @BeforeEach
    void setUp() {
        sessions = new SessionManager();
        authentication = new AuthenticationService(
                username -> credentials(username), (password, algorithm, iterations, salt, hash) ->
                        true,
                sessions);
        custodianRoute = new AtomicReference<>();
        loginRoute = new AtomicReference<>();
        router = new EvidenceLoggerApplication.AuthenticatedRoleRouter(
                authentication, custodianRoute::set, loginRoute::set);
    }

    @Test
    void custodianSessionRoutesToCustodianWorkspaceAndRemainsActive() {
        AuthenticatedSession session = authentication.signIn(
                "custodian", "accepted".toCharArray());

        router.accept(session);

        assertEquals(session, custodianRoute.get());
        assertEquals(session, sessions.requireSession());
        assertNull(loginRoute.get());
    }

    @Test
    void investigatorSessionIsClearedAndReturnsToLoginUntilWorkspaceExists() {
        AuthenticatedSession session = authentication.signIn(
                "investigator", "accepted".toCharArray());

        router.accept(session);

        assertNull(custodianRoute.get());
        assertFalse(sessions.currentSession().isPresent());
        assertEquals(
                "Investigator workspace is not available in this build.",
                loginRoute.get());
    }

    private static Optional<UserAccountCredentials> credentials(String username) {
        Role role = "custodian".equals(username)
                ? Role.EVIDENCE_CUSTODIAN
                : Role.INVESTIGATOR;
        String id = role == Role.EVIDENCE_CUSTODIAN
                ? "00000000-0000-0000-0000-000000000501"
                : "00000000-0000-0000-0000-000000000502";
        return Optional.of(new UserAccountCredentials(
                UserId.parse(id), username, role, "test", 1, SALT, HASH));
    }
}
