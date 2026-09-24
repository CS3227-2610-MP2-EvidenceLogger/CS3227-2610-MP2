package evidencelogger.repository;

import java.util.Optional;

/** Loads the minimum credential data needed for sign-in. */
public interface UserAccountRepository {
    /** Finds one account by its case-insensitive username. */
    Optional<UserAccountCredentials> findByUsername(String username);
}
