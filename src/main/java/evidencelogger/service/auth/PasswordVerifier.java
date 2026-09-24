package evidencelogger.service.auth;

/** Verifies a supplied password against stored password-derivation parameters. */
@FunctionalInterface
public interface PasswordVerifier {
    /** Returns whether the supplied password produces the expected hash. */
    boolean matches(
            char[] password,
            String algorithm,
            int iterations,
            byte[] salt,
            byte[] expectedHash);
}
