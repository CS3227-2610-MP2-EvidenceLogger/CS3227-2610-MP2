package evidencelogger.service.history;

import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.AuditEventType;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.history.AuditEventReadRepository;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.AuthorizationService;
import evidencelogger.service.dto.HistoryCommands;

/** Transactional Custodian corrections that never modify an earlier event. */
public final class DefaultHistoryCommandService implements HistoryCommandService {
    private final TransactionRunner transactions;
    private final AuthorizationService authorization;
    private final AuditEventReadRepository events;
    private final AuditEventWriter auditEvents;

    /** Creates the correction service with authorization and persistence collaborators. */
    public DefaultHistoryCommandService(
            TransactionRunner transactions,
            AuthorizationService authorization,
            AuditEventReadRepository events,
            AuditEventWriter auditEvents) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.events = Objects.requireNonNull(events, "events");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
    }

    @Override
    public void correctEvent(HistoryCommands.CorrectEvent command) {
        Objects.requireNonNull(command, "command");
        try {
            transactions.inTransaction(connection -> {
                AuthenticatedSession actor = authorization.requireCustodian();
                AuditEventReadRepository.EventSubjects target = events.findEventSubjects(
                                connection, command.eventId())
                        .orElseThrow(() -> new ServiceException.NotFound(
                                "The history entry no longer exists"));
                auditEvents.append(connection, actor, new AuditEventDraft(
                        AuditEventType.HISTORY_CORRECTED,
                        target.caseId(),
                        target.evidenceId(),
                        target.requestId(),
                        target.handoffId(),
                        target.checkoutId(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(command.reason()),
                        Optional.empty(),
                        Optional.of(command.correctionText()),
                        Optional.of(command.eventId()),
                        Optional.empty()));
                return null;
            });
        } catch (RepositoryException.StorageFailure exception) {
            throw new ServiceException.StorageFailure(
                    "The history correction could not be recorded", exception);
        }
    }
}
