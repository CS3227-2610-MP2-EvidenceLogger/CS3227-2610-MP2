package evidencelogger.service.auth;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;

/**
 * Central authorization checks used by command and query services. Implementors
 * must re-read current assignments where the check depends on case access.
 */
public interface AuthorizationService {
    AuthenticatedSession requireCustodian();

    AuthenticatedSession requireInvestigator();

    AuthenticatedSession requireAssignedInvestigator(CaseId caseId);

    AuthenticatedSession requireCollectingInvestigator(CheckoutId checkoutId);
}
