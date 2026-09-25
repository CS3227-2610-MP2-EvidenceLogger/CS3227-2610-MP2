package evidencelogger.service.auth;

import java.sql.Connection;
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

    /** Re-checks assignment using the caller-owned transaction connection. */
    AuthenticatedSession requireAssignedInvestigator(
            Connection connection, CaseId caseId);

    /** Verifies another Investigator's current assignment in the transaction. */
    void requireAssignedInvestigator(
            Connection connection, CaseId caseId, UserId investigatorId);

    AuthenticatedSession requireAssignedInvestigator(
            CaseId caseId, BiPredicate<CaseId, UserId> assignmentCheck);

    AuthenticatedSession requireCollectingInvestigator(CheckoutId checkoutId);

    /** Re-checks collector authorization using the caller-owned connection. */
    AuthenticatedSession requireCollectingInvestigator(
            Connection connection, CheckoutId checkoutId);

    AuthenticatedSession requireCollectingInvestigator(
            CheckoutId checkoutId, BiPredicate<CheckoutId, UserId> collectorCheck);
}
