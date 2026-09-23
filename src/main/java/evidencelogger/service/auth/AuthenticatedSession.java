package evidencelogger.service.auth;

import java.util.Objects;

import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;

/** Immutable identity established by successful authentication. */
public record AuthenticatedSession(UserId userId, Role role, String displayName) {
    /** Validates that every part of the authenticated identity is present. */
    public AuthenticatedSession {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(displayName, "displayName");
    }
}
