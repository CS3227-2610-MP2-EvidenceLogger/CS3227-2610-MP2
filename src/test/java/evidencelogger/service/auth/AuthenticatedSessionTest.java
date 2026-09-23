package evidencelogger.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;

class AuthenticatedSessionTest {
    @Test
    void sessionKeepsTheAuthenticatedIdentityAndRole() {
        UserId userId = new UserId(UUID.randomUUID());
        AuthenticatedSession session = new AuthenticatedSession(
                userId, Role.INVESTIGATOR, "Demo Investigator");

        assertEquals(userId, session.userId());
        assertEquals(Role.INVESTIGATOR, session.role());
        assertEquals("Demo Investigator", session.displayName());
    }

    @Test
    void sessionRejectsMissingIdentityData() {
        UserId userId = new UserId(UUID.randomUUID());

        assertThrows(NullPointerException.class, () ->
                new AuthenticatedSession(null, Role.INVESTIGATOR, "Demo Investigator"));
        assertThrows(NullPointerException.class, () ->
                new AuthenticatedSession(userId, null, "Demo Investigator"));
        assertThrows(NullPointerException.class, () ->
                new AuthenticatedSession(userId, Role.INVESTIGATOR, null));
    }
}
