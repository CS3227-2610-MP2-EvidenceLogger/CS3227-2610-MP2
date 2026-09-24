package evidencelogger.app;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import evidencelogger.domain.AuditEventId;
import evidencelogger.infrastructure.db.ConnectionFactory;
import evidencelogger.infrastructure.db.JdbcTransactionRunner;
import evidencelogger.infrastructure.db.MigrationRunner;
import evidencelogger.infrastructure.db.SqliteConnectionFactory;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.infrastructure.security.Pbkdf2PasswordVerifier;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.repository.jdbc.JdbcAuditEventWriter;
import evidencelogger.repository.jdbc.JdbcAuthorizationRepository;
import evidencelogger.repository.jdbc.JdbcUserAccountRepository;
import evidencelogger.service.auth.AuthenticationService;
import evidencelogger.service.auth.AuthorizationService;
import evidencelogger.service.auth.DefaultAuthorizationService;
import evidencelogger.service.auth.SessionManager;
import evidencelogger.service.history.AuditEventWriter;

/** Explicit application-wide object graph created after successful migration. */
public final class ApplicationComposition implements AutoCloseable {
    private final ConnectionFactory connectionFactory;
    private final TransactionRunner transactions;
    private final SessionManager sessions;
    private final AuthenticationService authentication;
    private final AuthorizationService authorization;
    private final AuditEventWriter auditEvents;

    private ApplicationComposition(
            ConnectionFactory connectionFactory,
            TransactionRunner transactions,
            SessionManager sessions,
            AuthenticationService authentication,
            AuthorizationService authorization,
            AuditEventWriter auditEvents) {
        this.connectionFactory = connectionFactory;
        this.transactions = transactions;
        this.sessions = sessions;
        this.authentication = authentication;
        this.authorization = authorization;
        this.auditEvents = auditEvents;
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
        AuditEventWriter auditEvents = new JdbcAuditEventWriter(
                sessions, clock, auditEventIds);
        return new ApplicationComposition(
                connectionFactory,
                transactions,
                sessions,
                authentication,
                authorization,
                auditEvents);
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

    /** Clears the current session during deterministic application shutdown. */
    @Override
    public void close() {
        authentication.signOut();
    }
}
