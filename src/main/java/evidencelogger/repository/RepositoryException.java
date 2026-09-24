package evidencelogger.repository;

import java.io.Serial;

/** Persistence failures translated without exposing JDBC implementation details. */
public abstract sealed class RepositoryException extends RuntimeException
        permits RepositoryException.Conflict, RepositoryException.NotFound,
                RepositoryException.StorageFailure {
    @Serial
    private static final long serialVersionUID = 1L;

    protected RepositoryException(String message) {
        super(message);
    }

    protected RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Indicates that a conditional write or uniqueness invariant failed. */
    public static final class Conflict extends RepositoryException {
        @Serial
        private static final long serialVersionUID = 1L;

        public Conflict(String message) {
            super(message);
        }
    }

    /** Indicates that a required record does not exist. */
    public static final class NotFound extends RepositoryException {
        @Serial
        private static final long serialVersionUID = 1L;

        public NotFound(String message) {
            super(message);
        }
    }

    /** Indicates an underlying persistence failure. */
    public static final class StorageFailure extends RepositoryException {
        @Serial
        private static final long serialVersionUID = 1L;

        public StorageFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
