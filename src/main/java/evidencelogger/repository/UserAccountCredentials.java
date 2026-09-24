package evidencelogger.repository;

import java.util.Objects;

import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;

/** Internal credential data loaded for authentication only. */
public record UserAccountCredentials(
        UserId userId,
        String displayName,
        Role role,
        String passwordAlgorithm,
        int passwordIterations,
        byte[] passwordSalt,
        byte[] passwordHash) {

    /** Validates and defensively copies credential material. */
    public UserAccountCredentials {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(passwordAlgorithm, "passwordAlgorithm");
        Objects.requireNonNull(passwordSalt, "passwordSalt");
        Objects.requireNonNull(passwordHash, "passwordHash");
        if (passwordIterations <= 0) {
            throw new IllegalArgumentException("passwordIterations must be positive");
        }
        passwordSalt = passwordSalt.clone();
        passwordHash = passwordHash.clone();
    }

    @Override
    public byte[] passwordSalt() {
        return passwordSalt.clone();
    }

    @Override
    public byte[] passwordHash() {
        return passwordHash.clone();
    }
}
