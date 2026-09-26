package evidencelogger.service.history;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.history.AuditEventReadRepository;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.AuthorizationService;
import evidencelogger.service.auth.SessionProvider;
import evidencelogger.service.dto.HistoryViews;

/** Authorized audit-history queries for Custodian and assigned Investigator views. */
public final class DefaultHistoryQueryService implements HistoryQueryService {
    private final TransactionRunner transactions;
    private final AuthorizationService authorization;
    private final SessionProvider sessions;
    private final AuditEventReadRepository reads;

    /** Creates history queries with authorization, session, and persistence collaborators. */
    public DefaultHistoryQueryService(
            TransactionRunner transactions,
            AuthorizationService authorization,
            SessionProvider sessions,
            AuditEventReadRepository reads) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.reads = Objects.requireNonNull(reads, "reads");
    }

    @Override
    public List<HistoryViews.Event> listEventsForCase(CaseId caseId) {
        Objects.requireNonNull(caseId, "caseId");
        Optional<UserId> scope = queryScope(caseId);
        try {
            return transactions.inTransaction(connection ->
                    reads.listEventsForCase(connection, caseId, scope)).stream()
                    .map(event -> new HistoryViews.Event(
                            event.eventId(),
                            event.type(),
                            event.actorDisplayName(),
                            event.actorRole(),
                            event.eventTime(),
                            event.correctionText(),
                            event.reason(),
                            event.correctedEventId()))
                    .toList();
        } catch (RepositoryException.StorageFailure exception) {
            throw new ServiceException.StorageFailure("History could not be read", exception);
        }
    }

    private Optional<UserId> queryScope(CaseId caseId) {
        AuthenticatedSession session = sessions.requireSession();
        if (session.role() == Role.EVIDENCE_CUSTODIAN) {
            authorization.requireCustodian();
            return Optional.empty();
        }
        if (session.role() == Role.INVESTIGATOR) {
            authorization.requireAssignedInvestigator(caseId);
            return Optional.of(session.userId());
        }
        throw new ServiceException.Forbidden("The signed-in role cannot read history");
    }
}
