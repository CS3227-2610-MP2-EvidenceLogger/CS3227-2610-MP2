package evidencelogger.service.checkout;

import java.util.List;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.service.dto.CheckoutViews;

/**
 * Authorized checkout reads. Implementations must constrain Investigator
 * results by the current session's assignments rather than relying on UI
 * filtering.
 */
public interface CheckoutQueryService {
    List<CheckoutViews.Request> listRequests();

    List<CheckoutViews.Request> listRequests(CheckoutRequestStatus status);

    List<CheckoutViews.Request> listRequestsForCase(CaseId caseId);

    CheckoutViews.Request getRequest(CheckoutRequestId requestId);

    List<CheckoutViews.Checkout> listCheckouts();

    List<CheckoutViews.Checkout> listCheckoutsForCase(CaseId caseId);

    CheckoutViews.Checkout getCheckout(CheckoutId checkoutId);

    List<CheckoutViews.ExaminationNote> listNotes(CheckoutId checkoutId);
}
