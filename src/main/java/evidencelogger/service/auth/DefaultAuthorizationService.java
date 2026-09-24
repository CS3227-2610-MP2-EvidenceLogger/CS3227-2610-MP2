package evidencelogger.service.auth;

import java.util.Objects;
import java.util.function.BiPredicate;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.repository.AuthorizationRepository;
import evidencelogger.service.ServiceException;

/** Central role, current-assignment, and collector authorization checks. */
public final class DefaultAuthorizationService implements AuthorizationService {
    private final SessionProvider sessions;
    private final AuthorizationRepository authorizationRepository;

    /** Creates authorization helpers over the shared session and current database facts. */
    public DefaultAuthorizationService(
            SessionProvider sessions,
            AuthorizationRepository authorizationRepository) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.authorizationRepository = Objects.requireNonNull(
                authorizationRepository, "authorizationRepository");
    }

    @Override
    public AuthenticatedSession requireCustodian() {
        return requireRole(Role.EVIDENCE_CUSTODIAN);
    }

    @Override
    public AuthenticatedSession requireInvestigator() {
        return requireRole(Role.INVESTIGATOR);
    }

    @Override
    public AuthenticatedSession requireAssignedInvestigator(CaseId caseId) {
        return requireAssignedInvestigator(caseId, authorizationRepository::isAssigned);
    }

    @Override
    public AuthenticatedSession requireAssignedInvestigator(
            CaseId caseId, BiPredicate<CaseId, UserId> assignmentCheck) {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(assignmentCheck, "assignmentCheck");
        AuthenticatedSession session = requireInvestigator();
        if (!assignmentCheck.test(caseId, session.userId())) {
            throw new ServiceException.Forbidden(
                    "The signed-in Investigator is not assigned to this case");
        }
        return session;
    }

    @Override
    public AuthenticatedSession requireCollectingInvestigator(CheckoutId checkoutId) {
        return requireCollectingInvestigator(
                checkoutId, authorizationRepository::isCollectingInvestigator);
    }

    @Override
    public AuthenticatedSession requireCollectingInvestigator(
            CheckoutId checkoutId, BiPredicate<CheckoutId, UserId> collectorCheck) {
        Objects.requireNonNull(checkoutId, "checkoutId");
        Objects.requireNonNull(collectorCheck, "collectorCheck");
        AuthenticatedSession session = requireInvestigator();
        if (!collectorCheck.test(checkoutId, session.userId())) {
            throw new ServiceException.Forbidden(
                    "The signed-in Investigator is not authorized for this checkout");
        }
        return session;
    }

    private AuthenticatedSession requireRole(Role expectedRole) {
        AuthenticatedSession session = sessions.requireSession();
        if (session.role() != expectedRole) {
            throw new ServiceException.Forbidden(
                    "The signed-in role cannot perform this operation");
        }
        return session;
    }
}
