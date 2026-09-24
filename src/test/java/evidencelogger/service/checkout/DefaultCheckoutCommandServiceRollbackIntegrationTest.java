package evidencelogger.service.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.ConnectionFactory;
import evidencelogger.infrastructure.db.JdbcTransactionRunner;
import evidencelogger.infrastructure.db.MigrationRunner;
import evidencelogger.infrastructure.db.SqliteConnectionFactory;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.repository.jdbc.JdbcAuditEventWriter;
import evidencelogger.repository.jdbc.JdbcAuthorizationRepository;
import evidencelogger.repository.jdbc.JdbcCheckoutRequestRepository;
import evidencelogger.repository.jdbc.JdbcEvidenceRepository;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.DefaultAuthorizationService;
import evidencelogger.service.auth.SessionProvider;
import evidencelogger.service.dto.CheckoutCommands;
import evidencelogger.service.history.AuditEventWriter;

class DefaultCheckoutCommandServiceRollbackIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-24T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UserId INVESTIGATOR_ID = userId("000000000002");
    private static final CaseId CASE_ID = caseId("000000000200");
    private static final EvidenceId EVIDENCE_ID = evidenceId("000000000202");
    private static final CheckoutRequestId REQUEST_ID = requestId("000000000203");

    @TempDir
    Path temporaryDirectory;

    private ConnectionFactory connections;
    private TransactionRunner transactions;
    private SessionProvider sessions;
    private JdbcCheckoutRequestRepository requests;

    @BeforeEach
    void setUp() throws SQLException {
        connections = new SqliteConnectionFactory(temporaryDirectory.resolve("rollback.db"));
        new MigrationRunner(connections, CLOCK).migrate();
        transactions = new JdbcTransactionRunner(connections);
        AuthenticatedSession session = new AuthenticatedSession(
                INVESTIGATOR_ID, Role.INVESTIGATOR, "Rollback Investigator");
        sessions = new SessionProvider() {
            @Override
            public java.util.Optional<AuthenticatedSession> currentSession() {
                return java.util.Optional.of(session);
            }

            @Override
            public AuthenticatedSession requireSession() {
                return session;
            }
        };
        requests = new JdbcCheckoutRequestRepository();
        seedBaseRows();
    }

    @Test
    void failedSubmissionRollsBackRequestAndAuditEvent() {
        DefaultCheckoutCommandService service = serviceWithFailingAuditWriter();

        assertThrows(ServiceException.StorageFailure.class, () -> service.submitRequest(
                new CheckoutCommands.SubmitRequest(
                        EVIDENCE_ID, "Review item", NOW.plusSeconds(3600))));

        assertEquals(0, count("checkout_request"));
        assertEquals(0, count("audit_event"));
    }

    @Test
    void failedWithdrawalRollsBackStatusAndAuditEvent() {
        insertPendingRequest();
        DefaultCheckoutCommandService service = serviceWithFailingAuditWriter();

        assertThrows(ServiceException.StorageFailure.class, () -> service.withdrawRequest(
                new CheckoutCommands.WithdrawRequest(REQUEST_ID)));

        assertEquals(CheckoutRequestStatus.PENDING, requestStatus());
        assertEquals(0, count("audit_event"));
    }

    private DefaultCheckoutCommandService serviceWithFailingAuditWriter() {
        JdbcAuditEventWriter delegate = new JdbcAuditEventWriter(
                sessions, CLOCK, () -> new AuditEventId(UUID.randomUUID()));
        AuditEventWriter failingWriter = (connection, event) -> {
            delegate.append(connection, event);
            throw new ServiceException.StorageFailure(
                    "Injected audit failure", new IllegalStateException("test failure"));
        };
        JdbcAuthorizationRepository authorizationRepository =
                new JdbcAuthorizationRepository(connections);
        return new DefaultCheckoutCommandService(
                transactions,
                new DefaultAuthorizationService(sessions, authorizationRepository),
                authorizationRepository,
                new JdbcEvidenceRepository(),
                requests,
                failingWriter,
                () -> REQUEST_ID,
                CLOCK);
    }

    private void seedBaseRows() throws SQLException {
        try (Connection connection = connections.open()) {
            execute(connection, """
                    INSERT INTO case_record(id, title, created_at) VALUES (?, ?, ?)
                    """, CASE_ID.toString(), "Rollback Case", NOW.toString());
            execute(connection, """
                    INSERT INTO case_assignment(case_id, investigator_id, assigned_at)
                    VALUES (?, ?, ?)
                    """, CASE_ID.toString(), INVESTIGATOR_ID.toString(), NOW.toString());
            execute(connection, """
                    INSERT INTO storage_location(id, name, created_at) VALUES (?, ?, ?)
                    """, UUID.randomUUID().toString(), "Rollback Locker", NOW.toString());
            String locationId = lastLocationId(connection);
            execute(connection, """
                    INSERT INTO evidence_item(
                        id, case_id, public_reference, description,
                        storage_location_id, custody_state, registered_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, EVIDENCE_ID.toString(), CASE_ID.toString(), "EV-ROLLBACK",
                    "Rollback evidence", locationId, "IN_STORAGE", NOW.toString());
        }
    }

    private void insertPendingRequest() {
        transactions.inTransaction(connection -> {
            requests.insertPending(connection, new evidencelogger.repository.checkout.CheckoutRequestRecord(
                    REQUEST_ID, EVIDENCE_ID, INVESTIGATOR_ID, "Review item",
                    NOW.plusSeconds(3600), CheckoutRequestStatus.PENDING, NOW));
            return null;
        });
    }

    private CheckoutRequestStatus requestStatus() {
        return transactions.inTransaction(connection ->
                requests.findById(connection, REQUEST_ID).orElseThrow().status());
    }

    private int count(String table) {
        return transactions.inTransaction(connection -> {
            try (var statement = connection.createStatement();
                    var results = statement.executeQuery("SELECT count(*) FROM " + table)) {
                results.next();
                return results.getInt(1);
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static String lastLocationId(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var results = statement.executeQuery("SELECT id FROM storage_location")) {
            results.next();
            return results.getString(1);
        }
    }

    private static void execute(Connection connection, String sql, String... values)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private static UserId userId(String suffix) {
        return UserId.parse("00000000-0000-0000-0000-" + suffix);
    }

    private static CaseId caseId(String suffix) {
        return CaseId.parse("00000000-0000-0000-0000-" + suffix);
    }

    private static EvidenceId evidenceId(String suffix) {
        return EvidenceId.parse("00000000-0000-0000-0000-" + suffix);
    }

    private static CheckoutRequestId requestId(String suffix) {
        return CheckoutRequestId.parse("00000000-0000-0000-0000-" + suffix);
    }
}
