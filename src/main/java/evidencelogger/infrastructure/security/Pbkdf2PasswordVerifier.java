package evidencelogger.infrastructure.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.PasswordVerifier;

/** Verifies versioned PBKDF2 password hashes using constant-time comparison. */
public final class Pbkdf2PasswordVerifier implements PasswordVerifier {
    private static final String SUPPORTED_ALGORITHM = "PBKDF2WithHmacSHA256";

    @Override
    public boolean matches(
            char[] password,
            String algorithm,
            int iterations,
            byte[] salt,
            byte[] expectedHash) {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(salt, "salt");
        Objects.requireNonNull(expectedHash, "expectedHash");
        if (!SUPPORTED_ALGORITHM.equals(algorithm) || iterations <= 0 || expectedHash.length == 0) {
            throw new ServiceException.StorageFailure(
                    "Stored credential parameters are unsupported",
                    new IllegalArgumentException("Invalid password derivation parameters"));
        }
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterations, expectedHash.length * Byte.SIZE);
        byte[] actualHash = null;
        try {
            actualHash = SecretKeyFactory.getInstance(algorithm)
                    .generateSecret(keySpec).getEncoded();
            return MessageDigest.isEqual(actualHash, expectedHash);
        } catch (GeneralSecurityException exception) {
            throw new ServiceException.StorageFailure(
                    "Stored credentials could not be verified", exception);
        } finally {
            if (actualHash != null) {
                Arrays.fill(actualHash, (byte) 0);
            }
            keySpec.clearPassword();
        }
    }
}
