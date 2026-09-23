package evidencelogger.service;

import java.io.Serial;

/** Typed failures exposed by application services to the UI. */
public abstract sealed class ServiceException extends RuntimeException
        permits ServiceException.Unauthenticated, ServiceException.Forbidden,
                ServiceException.ValidationFailure, ServiceException.InvalidTransition,
                ServiceException.Conflict, ServiceException.NotFound,
                ServiceException.StorageFailure {
    @Serial
    private static final long serialVersionUID = 1L;

    protected ServiceException(String message) {
        super(message);
    }

    protected ServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Indicates that a protected operation has no authenticated session. */
    public static final class Unauthenticated extends ServiceException {
        @Serial
        private static final long serialVersionUID = 1L;

        public Unauthenticated(String message) {
            super(message);
        }
    }

    /** Indicates that the authenticated actor is not authorized. */
    public static final class Forbidden extends ServiceException {
        @Serial
        private static final long serialVersionUID = 1L;

        public Forbidden(String message) {
            super(message);
        }
    }

    /** Indicates that user input violates an application validation rule. */
    public static final class ValidationFailure extends ServiceException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ValidationFailure(String message) {
            super(message);
        }
    }

    /** Indicates that a requested workflow transition is not legal. */
    public static final class InvalidTransition extends ServiceException {
        @Serial
        private static final long serialVersionUID = 1L;

        public InvalidTransition(String message) {
            super(message);
        }
    }

    /** Indicates that current persisted state conflicts with the command. */
    public static final class Conflict extends ServiceException {
        @Serial
        private static final long serialVersionUID = 1L;

        public Conflict(String message) {
            super(message);
        }
    }

    /** Indicates that a requested domain record does not exist. */
    public static final class NotFound extends ServiceException {
        @Serial
        private static final long serialVersionUID = 1L;

        public NotFound(String message) {
            super(message);
        }
    }

    /** Indicates that persistence failed while processing an operation. */
    public static final class StorageFailure extends ServiceException {
        @Serial
        private static final long serialVersionUID = 1L;

        public StorageFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
