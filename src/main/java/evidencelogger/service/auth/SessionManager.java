package evidencelogger.service.auth;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import evidencelogger.service.ServiceException;

/** Owns the application's single current authenticated session. */
public final class SessionManager implements SessionProvider {
    private final AtomicReference<AuthenticatedSession> currentSession = new AtomicReference<>();

    @Override
    public Optional<AuthenticatedSession> currentSession() {
        return Optional.ofNullable(currentSession.get());
    }

    @Override
    public AuthenticatedSession requireSession() {
        AuthenticatedSession session = currentSession.get();
        if (session == null) {
            throw new ServiceException.Unauthenticated("Sign in is required");
        }
        return session;
    }

    /** Clears the current session, including during logout and shutdown. */
    public void clear() {
        currentSession.set(null);
    }

    void establish(AuthenticatedSession session) {
        currentSession.set(Objects.requireNonNull(session, "session"));
    }
}
