package evidencelogger.app;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.infrastructure.db.ConnectionFactory;
import evidencelogger.infrastructure.db.JdbcTransactionRunner;
import evidencelogger.infrastructure.db.MigrationRunner;
import evidencelogger.infrastructure.db.SqliteConnectionFactory;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.infrastructure.security.Pbkdf2PasswordVerifier;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.repository.jdbc.JdbcAuditEventReadRepository;
import evidencelogger.repository.jdbc.JdbcAuditEventWriter;
import evidencelogger.repository.jdbc.JdbcAuthorizationRepository;
import evidencelogger.repository.jdbc.JdbcCaseworkRepository;
import evidencelogger.repository.jdbc.JdbcCheckoutReadRepository;
import evidencelogger.repository.jdbc.JdbcCheckoutRepository;
import evidencelogger.repository.jdbc.JdbcCheckoutRequestRepository;
import evidencelogger.repository.jdbc.JdbcEvidenceRepository;
import evidencelogger.repository.jdbc.JdbcExaminationNoteRepository;
import evidencelogger.repository.jdbc.JdbcHandoffRepository;
import evidencelogger.repository.jdbc.JdbcReturnInspectionRepository;
import evidencelogger.repository.jdbc.JdbcUserAccountRepository;
import evidencelogger.service.auth.AuthenticationService;
import evidencelogger.service.auth.AuthorizationService;
import evidencelogger.service.auth.DefaultAuthorizationService;
import evidencelogger.service.auth.SessionManager;
import evidencelogger.service.casework.CaseworkCommandService;
import evidencelogger.service.casework.CaseworkQueryService;
import evidencelogger.service.casework.DefaultCaseworkService;
import evidencelogger.service.checkout.CheckoutCommandService;
import evidencelogger.service.checkout.CheckoutQueryService;
import evidencelogger.service.checkout.DefaultCheckoutCommandService;
import evidencelogger.service.checkout.DefaultCheckoutQueryService;
import evidencelogger.service.history.AuditEventWriter;
import evidencelogger.service.history.DefaultHistoryQueryService;
import evidencelogger.service.history.HistoryQueryService;

/** Explicit application-wide object graph created after successful migration. */
public final class ApplicationComposition implements AutoCloseable {
    private final ConnectionFactory connectionFactory;
    private final TransactionRunner transactions;
    private final SessionManager sessions;
    private final AuthenticationService authentication;
    private final AuthorizationService authorization;
    private final AuditEventWriter auditEvents;
    private final CaseworkCommandService caseworkCommands;
    private final CaseworkQueryService caseworkQueries;
    private final CheckoutCommandService checkoutCommands;
    private final CheckoutQueryService checkoutQueries;
    private final HistoryQueryService historyQueries;

    private ApplicationComposition(
            ConnectionFactory connectionFactory,
            TransactionRunner transactions,
            SessionManager sessions,
            AuthenticationService authentication,
            AuthorizationService authorization,
            AuditEventWriter auditEvents,
            CaseworkCommandService caseworkCommands,
            CaseworkQueryService caseworkQueries,
            CheckoutCommandService checkoutCommands,
            CheckoutQueryService checkoutQueries,
            HistoryQueryService historyQueries) {
        this.connectionFactory = connectionFactory;
        this.transactions = transactions;
        this.sessions = sessions;
        this.authentication = authentication;
        this.authorization = authorization;
        this.auditEvents = auditEvents;
        this.caseworkCommands = caseworkCommands;
        this.caseworkQueries = caseworkQueries;
        this.checkoutCommands = checkoutCommands;
        this.checkoutQueries = checkoutQueries;
        this.historyQueries = historyQueries;
    }

    /** Runs migrations first, then creates the shared application service graph. */
    public static ApplicationComposition start(Path databasePath, Clock clock) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(clock, "clock");
        ConnectionFactory connectionFactory = new SqliteConnectionFactory(databasePath);
        new MigrationRunner(connectionFactory, clock).migrate();

        SessionManager sessions = new SessionManager();
        TransactionRunner transactions = new JdbcTransactionRunner(connectionFactory);
        AuthenticationService authentication = new AuthenticationService(
                new JdbcUserAccountRepository(connectionFactory),
                new Pbkdf2PasswordVerifier(),
                sessions);
        AuthorizationService authorization = new DefaultAuthorizationService(
                sessions, new JdbcAuthorizationRepository(connectionFactory));
        IdGenerator<AuditEventId> auditEventIds = () -> new AuditEventId(UUID.randomUUID());
        IdGenerator<CaseId> caseIds = () -> new CaseId(UUID.randomUUID());
        IdGenerator<StorageLocationId> locationIds = () ->
                new StorageLocationId(UUID.randomUUID());
        IdGenerator<EvidenceId> evidenceIds = () -> new EvidenceId(UUID.randomUUID());
        IdGenerator<CheckoutRequestId> requestIds = () -> new CheckoutRequestId(UUID.randomUUID());
        IdGenerator<HandoffId> handoffIds = () -> new HandoffId(UUID.randomUUID());
        IdGenerator<CheckoutId> checkoutIds = () -> new CheckoutId(UUID.randomUUID());
        IdGenerator<ExaminationNoteId> noteIds = () -> new ExaminationNoteId(UUID.randomUUID());
        AuditEventWriter auditEvents = new JdbcAuditEventWriter(
                clock, auditEventIds);
        DefaultCaseworkService casework = new DefaultCaseworkService(
                new JdbcCaseworkRepository(connectionFactory),
                transactions,
                authorization,
                sessions,
                auditEvents,
                clock,
                caseIds,
                locationIds,
                evidenceIds);
        CheckoutQueryService checkoutQueries = new DefaultCheckoutQueryService(
                transactions,
                authorization,
                sessions,
                new JdbcCheckoutReadRepository());
        CheckoutCommandService checkoutCommands = new DefaultCheckoutCommandService(
                transactions,
                authorization,
                new JdbcEvidenceRepository(),
                new JdbcCheckoutRequestRepository(),
                new JdbcHandoffRepository(),
                new JdbcCheckoutRepository(),
                new JdbcExaminationNoteRepository(),
                new JdbcReturnInspectionRepository(),
                auditEvents,
                requestIds,
                handoffIds,
                checkoutIds,
                noteIds,
                clock);
        HistoryQueryService historyQueries = new DefaultHistoryQueryService(
                transactions,
                authorization,
                sessions,
                new JdbcAuditEventReadRepository());
        return new ApplicationComposition(
                connectionFactory,
                transactions,
                sessions,
                authentication,
                authorization,
                auditEvents,
                casework,
                casework,
                checkoutCommands,
                checkoutQueries,
                historyQueries);
    }

    /** Returns the shared connection factory. */
    public ConnectionFactory connectionFactory() {
        return connectionFactory;
    }

    /** Returns the shared transaction runner. */
    public TransactionRunner transactions() {
        return transactions;
    }

    /** Returns the single application-wide session manager. */
    public SessionManager sessions() {
        return sessions;
    }

    /** Returns the authentication service using the shared session manager. */
    public AuthenticationService authentication() {
        return authentication;
    }

    /** Returns the authorization service using the shared session manager. */
    public AuthorizationService authorization() {
        return authorization;
    }

    /** Returns the audit writer using the shared session manager and clock. */
    public AuditEventWriter auditEvents() {
        return auditEvents;
    }

    /** Returns the authorized casework command service. */
    public CaseworkCommandService caseworkCommands() {
        return caseworkCommands;
    }

    /** Returns the authorized casework query service. */
    public CaseworkQueryService caseworkQueries() {
        return caseworkQueries;
    }

    /** Returns authorized checkout commands. */
    public CheckoutCommandService checkoutCommands() {
        return checkoutCommands;
    }

    /** Returns the authorized checkout query service. */
    public CheckoutQueryService checkoutQueries() {
        return checkoutQueries;
    }

    /** Returns authorized append-only history queries. */
    public HistoryQueryService historyQueries() {
        return historyQueries;
    }

    /** Clears the current session during deterministic application shutdown. */
    @Override
    public void close() {
        authentication.signOut();
    }
}
