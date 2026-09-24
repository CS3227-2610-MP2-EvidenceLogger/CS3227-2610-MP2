package evidencelogger.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceId;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutRequestRecord;

class JdbcCheckoutRequestRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");
    private static final EvidenceId EVIDENCE_ID =
            EvidenceId.parse("00000000-0000-0000-0000-000000000510");
    private static final CheckoutRequestId FIRST_REQUEST_ID =
            CheckoutRequestId.parse("00000000-0000-0000-0000-000000000511");
    private static final CheckoutRequestId SECOND_REQUEST_ID =
            CheckoutRequestId.parse("00000000-0000-0000-0000-000000000512");

    @TempDir
    Path temporaryDirectory;

    private CheckoutRepositoryTestDatabase database;
    private JdbcCheckoutRequestRepository repository;

    @BeforeEach
    void setUp() {
        database = new CheckoutRepositoryTestDatabase(
                temporaryDirectory.resolve("checkout-request.db"));
        database.insertEvidence(EVIDENCE_ID);
        repository = new JdbcCheckoutRequestRepository();
    }

    @Test
    void insertsMapsAndOrdersRequestHistoryOnProductionSchema() {
        CheckoutRequestRecord firstPending = request(
                FIRST_REQUEST_ID, NOW, CheckoutRequestStatus.PENDING);
        CheckoutRequestRecord firstRejected = request(
                FIRST_REQUEST_ID, NOW, CheckoutRequestStatus.REJECTED);
        CheckoutRequestRecord secondPending = request(
                SECOND_REQUEST_ID, NOW.plusSeconds(1), CheckoutRequestStatus.PENDING);

        database.inTransaction(connection -> {
            repository.insertPending(connection, firstPending);
            assertTrue(repository.transitionStatus(
                    connection,
                    firstPending.requestId(),
                    CheckoutRequestStatus.PENDING,
                    CheckoutRequestStatus.REJECTED));
            repository.insertPending(connection, secondPending);
            return null;
        });

        assertEquals(firstRejected, database.inTransaction(connection ->
                repository.findById(connection, FIRST_REQUEST_ID).orElseThrow()));
        assertEquals(List.of(firstRejected, secondPending), database.inTransaction(connection ->
                repository.findForEvidence(connection, EVIDENCE_ID)));
        assertEquals(secondPending, database.inTransaction(connection ->
                repository.findPendingOrApprovedForEvidence(
                        connection, EVIDENCE_ID).orElseThrow()));
    }

    @Test
    void conditionalTransitionOnlyAdvancesTheExpectedState() {
        CheckoutRequestRecord request = request(
                FIRST_REQUEST_ID, NOW, CheckoutRequestStatus.PENDING);

        database.inTransaction(connection -> {
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
            return null;
        });
    }

    @Test
    void duplicateRequestIdentifiersBecomeTypedConflicts() {
        CheckoutRequestRecord request = request(
                FIRST_REQUEST_ID, NOW, CheckoutRequestStatus.PENDING);
        database.inTransaction(connection -> {
            repository.insertPending(connection, request);
            return null;
        });

        Executable duplicateInsert = () -> database.inTransaction(connection -> {
            repository.insertPending(connection, request);
            return null;
        });
        RepositoryException.Conflict conflict = assertThrows(
                RepositoryException.Conflict.class, duplicateInsert);

        assertEquals("checkout request conflicts with an existing record", conflict.getMessage());
    }

    private static CheckoutRequestRecord request(
            CheckoutRequestId requestId,
            Instant submittedAt,
            CheckoutRequestStatus status) {
        return new CheckoutRequestRecord(
                requestId,
                EVIDENCE_ID,
                CheckoutRepositoryTestDatabase.INVESTIGATOR_ID,
                "Review item",
                submittedAt.plusSeconds(3600),
                status,
                submittedAt);
    }
}
