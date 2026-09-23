package evidencelogger.service.auth;

import java.util.Optional;

/** Read-only access to the single service-owned current session. */
public interface SessionProvider {
    Optional<AuthenticatedSession> currentSession();

    AuthenticatedSession requireSession();
}
