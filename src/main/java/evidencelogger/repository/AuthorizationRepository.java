package evidencelogger.repository;

import java.sql.Connection;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.UserId;

/** Current persisted relationships used by service authorization checks. */
public interface AuthorizationRepository {
    /** Returns whether the Investigator is currently assigned to the case. */
    boolean isAssigned(CaseId caseId, UserId investigatorId);

    /** Returns the assignment fact using the caller-owned transaction connection. */
    boolean isAssigned(Connection connection, CaseId caseId, UserId investigatorId);

    /** Returns whether the Investigator collected the checkout and remains assigned. */
    boolean isCollectingInvestigator(CheckoutId checkoutId, UserId investigatorId);

    /** Returns the collector fact using the caller-owned transaction connection. */
    boolean isCollectingInvestigator(
            Connection connection, CheckoutId checkoutId, UserId investigatorId);
}
