package evidencelogger.ui.login;

import java.util.Objects;

import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.AuthenticationService;
import evidencelogger.ui.common.ServiceFailurePresenter;

/** Presentation logic for credential-based sign-in. */
public final class LoginController {
    private final AuthenticationService authentication;

    /** Creates a controller backed by the shared authentication service. */
    public LoginController(AuthenticationService authentication) {
        this.authentication = Objects.requireNonNull(authentication, "authentication");
    }

    /** Attempts sign-in and converts expected failures into user-readable results. */
    public Result signIn(String username, char[] password) {
        Objects.requireNonNull(password, "password");
        try {
            return Result.success(authentication.signIn(username, password));
        } catch (ServiceException exception) {
            return Result.failure(ServiceFailurePresenter.messageFor(exception, "Sign-in"));
        }
    }

    /** Clears an authenticated session that cannot yet be routed to a workspace. */
    public void signOut() {
        authentication.signOut();
    }

    /** Outcome rendered by the login view without exposing service exceptions. */
    public record Result(boolean successful, AuthenticatedSession session, String message) {
        /** Validates the presentation outcome. */
        public Result {
            Objects.requireNonNull(message, "message");
            if (successful) {
                Objects.requireNonNull(session, "session");
            }
        }

        private static Result success(AuthenticatedSession session) {
            return new Result(true, session, "");
        }

        private static Result failure(String message) {
            return new Result(false, null, message);
        }
    }
}
