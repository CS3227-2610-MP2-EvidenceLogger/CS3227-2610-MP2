package evidencelogger.service.auth;

import java.util.Objects;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.Role;
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
        Objects.requireNonNull(caseId, "caseId");
        AuthenticatedSession session = requireInvestigator();
        if (!authorizationRepository.isAssigned(caseId, session.userId())) {
            throw new ServiceException.Forbidden(
                    "The signed-in Investigator is not assigned to this case");
        }
        return session;
    }

    @Override
    public AuthenticatedSession requireCollectingInvestigator(CheckoutId checkoutId) {
        Objects.requireNonNull(checkoutId, "checkoutId");
        AuthenticatedSession session = requireInvestigator();
        if (!authorizationRepository.isCollectingInvestigator(checkoutId, session.userId())) {
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
