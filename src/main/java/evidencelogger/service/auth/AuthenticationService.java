package evidencelogger.service.auth;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.repository.UserAccountCredentials;
import evidencelogger.repository.UserAccountRepository;
import evidencelogger.service.ServiceException;

/** Authenticates credentials and owns session establishment and logout. */
public final class AuthenticationService {
    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final UserAccountRepository userAccounts;
    private final PasswordVerifier passwordVerifier;
    private final SessionManager sessions;

    /** Creates the authentication service and its explicit collaborators. */
    public AuthenticationService(
            UserAccountRepository userAccounts,
            PasswordVerifier passwordVerifier,
            SessionManager sessions) {
        this.userAccounts = Objects.requireNonNull(userAccounts, "userAccounts");
        this.passwordVerifier = Objects.requireNonNull(passwordVerifier, "passwordVerifier");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /** Authenticates credentials, installs one session, and clears the password array. */
    public AuthenticatedSession signIn(String username, char[] password) {
        Objects.requireNonNull(password, "password");
        sessions.clear();
        try {
            String suppliedUsername = username == null ? "" : username.strip();
            Optional<UserAccountCredentials> found = suppliedUsername.isEmpty()
                    ? Optional.empty()
                    : userAccounts.findByUsername(suppliedUsername);
            if (found.isEmpty()) {
                throw new ServiceException.Unauthenticated(INVALID_CREDENTIALS);
            }
            UserAccountCredentials credentials = found.orElseThrow();
            boolean verified = passwordVerifier.matches(
                    password,
                    credentials.passwordAlgorithm(),
                    credentials.passwordIterations(),
                    credentials.passwordSalt(),
                    credentials.passwordHash());
            if (!verified) {
                throw new ServiceException.Unauthenticated(INVALID_CREDENTIALS);
            }
            AuthenticatedSession session = new AuthenticatedSession(
                    credentials.userId(), credentials.role(), credentials.displayName());
            sessions.establish(session);
            return session;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    /** Clears the current session. */
    public void signOut() {
        sessions.clear();
    }
}
