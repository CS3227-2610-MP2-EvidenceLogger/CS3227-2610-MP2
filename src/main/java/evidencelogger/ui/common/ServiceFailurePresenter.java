package evidencelogger.ui.common;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import evidencelogger.service.ServiceException;

/** Converts typed service failures into safe messages and correlated diagnostics. */
public final class ServiceFailurePresenter {
    private static final Logger LOGGER = Logger.getLogger(
            ServiceFailurePresenter.class.getName());

    private ServiceFailurePresenter() {
    }

    /** Returns a user-readable message and logs storage failures with a reference ID. */
    public static String messageFor(ServiceException exception, String operationName) {
        Objects.requireNonNull(exception, "exception");
        Objects.requireNonNull(operationName, "operationName");
        if (!(exception instanceof ServiceException.StorageFailure)) {
            return exception.getMessage();
        }

        String diagnosticId = UUID.randomUUID().toString();
        LOGGER.log(Level.SEVERE,
                operationName + " storage failure [operationId=" + diagnosticId + "]",
                exception);
        return exception.getMessage() + ". Reference: " + diagnosticId;
    }
}
