package evidencelogger.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.ConnectionFactory;
import evidencelogger.infrastructure.db.JdbcTransactionRunner;
import evidencelogger.infrastructure.db.MigrationRunner;
import evidencelogger.infrastructure.db.SqliteConnectionFactory;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.SessionProvider;
import evidencelogger.service.history.AuditEventDraft;

class JdbcAuditEventWriterIntegrationTest {
    private static final Instant EVENT_TIME = Instant.parse("2026-09-24T14:00:00Z");
    private static final AuditEventId EVENT_ID =
            AuditEventId.parse("00000000-0000-0000-0000-000000000300");
    private static final UserId ACTOR_ID =
            UserId.parse("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path temporaryDirectory;

    private ConnectionFactory connectionFactory;
    private TransactionRunner transactions;
    private JdbcAuditEventWriter writer;

    @BeforeEach
    void createDatabaseAndWriter() {
        connectionFactory = new SqliteConnectionFactory(temporaryDirectory.resolve("audit.db"));
        new MigrationRunner(connectionFactory, Clock.systemUTC()).migrate();
        transactions = new JdbcTransactionRunner(connectionFactory);
        AuthenticatedSession currentSession = new AuthenticatedSession(
                ACTOR_ID, Role.EVIDENCE_CUSTODIAN, "Morgan Custodian");
        SessionProvider sessions = new SessionProvider() {
            @Override
            public Optional<AuthenticatedSession> currentSession() {
                return Optional.of(currentSession);
            }

            @Override
            public AuthenticatedSession requireSession() {
                return currentSession;
            }
        };
        IdGenerator<AuditEventId> eventIds = () -> EVENT_ID;
        writer = new JdbcAuditEventWriter(
                sessions, Clock.fixed(EVENT_TIME, ZoneOffset.UTC), eventIds);
    }

    @Test
    void appendUsesActiveActorClockAndIdInsideCallerTransaction() throws SQLException {
        AuditEventId actualId = transactions.inTransaction(
                connection -> writer.append(connection, emptyDraft(AuditEventType.CASE_CREATED)));

        assertEquals(EVENT_ID, actualId);
        try (Connection connection = connectionFactory.open();
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery("""
                        SELECT id, event_type, actor_id, actor_role, event_time FROM audit_event
                        """)) {
            results.next();
            assertEquals(EVENT_ID.toString(), results.getString("id"));
            assertEquals(AuditEventType.CASE_CREATED.name(), results.getString("event_type"));
            assertEquals(ACTOR_ID.toString(), results.getString("actor_id"));
            assertEquals(Role.EVIDENCE_CUSTODIAN.name(), results.getString("actor_role"));
            assertEquals(EVENT_TIME.toString(), results.getString("event_time"));
        }
    }

    @Test
    void laterFailureRollsBackTheAuditAppend() throws SQLException {
        assertThrows(IllegalStateException.class, () -> transactions.inTransaction(connection -> {
            writer.append(connection, emptyDraft(AuditEventType.CASE_CREATED));
            throw new IllegalStateException("injected failure after audit append");
        }));

        try (Connection connection = connectionFactory.open();
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery("SELECT count(*) FROM audit_event")) {
            assertEquals(0, results.getInt(1));
        }
    }

    @Test
    void unauthenticatedAppendWritesNothing() throws SQLException {
        IdGenerator<AuditEventId> eventIds = () -> EVENT_ID;
        JdbcAuditEventWriter unauthenticatedWriter = new JdbcAuditEventWriter(
                new SessionProvider() {
                    @Override
                    public Optional<AuthenticatedSession> currentSession() {
                        return Optional.empty();
                    }

                    @Override
                    public AuthenticatedSession requireSession() {
                        throw new ServiceException.Unauthenticated("Sign in is required");
                    }
                },
                Clock.fixed(EVENT_TIME, ZoneOffset.UTC),
                eventIds);

        assertThrows(ServiceException.Unauthenticated.class, () -> transactions.inTransaction(
                connection -> unauthenticatedWriter.append(
                        connection, emptyDraft(AuditEventType.CASE_CREATED))));

        try (Connection connection = connectionFactory.open();
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery("SELECT count(*) FROM audit_event")) {
            assertEquals(0, results.getInt(1));
        }
    }

    private static AuditEventDraft emptyDraft(AuditEventType type) {
        return new AuditEventDraft(
                type,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
