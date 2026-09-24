package evidencelogger.service.auth;

import java.util.function.BiPredicate;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.UserId;

/**
 * Central authorization checks used by command and query services. Implementors
 * must re-read current assignments where the check depends on case access.
 */
public interface AuthorizationService {
    AuthenticatedSession requireCustodian();

    AuthenticatedSession requireInvestigator();

    AuthenticatedSession requireAssignedInvestigator(CaseId caseId);

    AuthenticatedSession requireAssignedInvestigator(
            CaseId caseId, BiPredicate<CaseId, UserId> assignmentCheck);

    AuthenticatedSession requireCollectingInvestigator(CheckoutId checkoutId);

    AuthenticatedSession requireCollectingInvestigator(
            CheckoutId checkoutId, BiPredicate<CheckoutId, UserId> collectorCheck);
}
