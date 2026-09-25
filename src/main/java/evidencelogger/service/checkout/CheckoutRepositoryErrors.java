package evidencelogger.service.checkout;

import evidencelogger.repository.RepositoryException;
import evidencelogger.service.ServiceException;

/** Translates checkout persistence failures at the service boundary. */
final class CheckoutRepositoryErrors {
    private CheckoutRepositoryErrors() {
    }

    static ServiceException translate(RepositoryException exception) {
        if (exception instanceof RepositoryException.Conflict) {
            return new ServiceException.Conflict(exception.getMessage());
        }
        if (exception instanceof RepositoryException.NotFound) {
            return new ServiceException.NotFound(exception.getMessage());
        }
        return new ServiceException.StorageFailure(exception.getMessage(), exception);
    }
}
