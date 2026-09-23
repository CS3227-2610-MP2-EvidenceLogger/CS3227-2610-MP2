package evidencelogger.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.UserId;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutRequestRecord;

class JdbcCheckoutRequestRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

    private Connection connection;
    private JdbcCheckoutRequestRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE checkout_request ("
                    + "request_id TEXT PRIMARY KEY, evidence_id TEXT NOT NULL, "
                    + "requester_id TEXT NOT NULL, purpose TEXT NOT NULL, "
                    + "expected_return_at TEXT NOT NULL, status TEXT NOT NULL, "
                    + "submitted_at TEXT NOT NULL)");
        }
        repository = new JdbcCheckoutRequestRepository();
    }

    @Test
    void insertsMapsAndOrdersRequestHistory() {
        EvidenceId evidenceId = evidenceId();
        CheckoutRequestRecord first = request(evidenceId, NOW, CheckoutRequestStatus.PENDING);
        CheckoutRequestRecord second = request(
                evidenceId, NOW.plusSeconds(1), CheckoutRequestStatus.REJECTED);

        repository.insertPending(connection, first);
        repository.insertPending(connection, second);

        assertEquals(first, repository.findById(connection, first.requestId()).orElseThrow());
        assertEquals(List.of(first, second), repository.findForEvidence(connection, evidenceId));
        assertEquals(first, repository.findPendingOrApprovedForEvidence(
                connection, evidenceId).orElseThrow());
    }

    @Test
    void conditionalTransitionOnlyAdvancesTheExpectedState() {
        CheckoutRequestRecord request = request(
                evidenceId(), NOW, CheckoutRequestStatus.PENDING);
        repository.insertPending(connection, request);

        assertTrue(repository.transitionStatus(
                connection,
                request.requestId(),
                CheckoutRequestStatus.PENDING,
                CheckoutRequestStatus.APPROVED));
        assertFalse(repository.transitionStatus(
                connection,
                request.requestId(),
                CheckoutRequestStatus.PENDING,
                CheckoutRequestStatus.REJECTED));
    }

    @Test
    void duplicateRequestIdentifiersBecomeTypedConflicts() {
        CheckoutRequestRecord request = request(
                evidenceId(), NOW, CheckoutRequestStatus.PENDING);
        repository.insertPending(connection, request);

        Executable duplicateInsert = () -> repository.insertPending(connection, request);
        RepositoryException.Conflict conflict = assertThrows(
                RepositoryException.Conflict.class, duplicateInsert);

        assertEquals("checkout request conflicts with an existing record", conflict.getMessage());
    }

    private static CheckoutRequestRecord request(
            EvidenceId evidenceId, Instant submittedAt, CheckoutRequestStatus status) {
        return new CheckoutRequestRecord(
                new CheckoutRequestId(UUID.randomUUID()),
                evidenceId,
                new UserId(UUID.randomUUID()),
                "Review item",
                submittedAt.plusSeconds(3600),
                status,
                submittedAt);
    }

    private static EvidenceId evidenceId() {
        return new EvidenceId(UUID.randomUUID());
    }
}
