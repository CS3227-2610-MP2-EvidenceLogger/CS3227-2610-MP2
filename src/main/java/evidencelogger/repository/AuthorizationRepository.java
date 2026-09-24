package evidencelogger.repository;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.UserId;

/** Current persisted relationships used by service authorization checks. */
public interface AuthorizationRepository {
    /** Returns whether the Investigator is currently assigned to the case. */
    boolean isAssigned(CaseId caseId, UserId investigatorId);

    /** Returns whether the Investigator collected the checkout and remains assigned. */
    boolean isCollectingInvestigator(CheckoutId checkoutId, UserId investigatorId);
}
