package evidencelogger.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.HandoffId;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutRecord;
import evidencelogger.repository.checkout.CheckoutRequestRecord;
import evidencelogger.repository.checkout.HandoffRecord;

class JdbcHandoffAndCheckoutRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");
    private static final EvidenceId EVIDENCE_ID =
            EvidenceId.parse("00000000-0000-0000-0000-000000000520");
    private static final CheckoutRequestId REQUEST_ID =
            CheckoutRequestId.parse("00000000-0000-0000-0000-000000000521");
    private static final HandoffId HANDOFF_ID =
            HandoffId.parse("00000000-0000-0000-0000-000000000522");
    private static final CheckoutId CHECKOUT_ID =
            CheckoutId.parse("00000000-0000-0000-0000-000000000523");

    @TempDir
    Path temporaryDirectory;

    private CheckoutRepositoryTestDatabase database;
    private JdbcCheckoutRequestRepository requests;
    private JdbcHandoffRepository handoffs;
    private JdbcCheckoutRepository checkouts;

    @BeforeEach
    void setUp() {
        database = new CheckoutRepositoryTestDatabase(
                temporaryDirectory.resolve("handoff-checkout.db"));
        database.insertEvidence(EVIDENCE_ID);
        requests = new JdbcCheckoutRequestRepository();
        handoffs = new JdbcHandoffRepository();
        checkouts = new JdbcCheckoutRepository();
        insertApprovedRequest();
    }

    @Test
    void handoffAcknowledgmentAndReversalAreConditional() {
        HandoffRecord handoff = handoff();
        database.inTransaction(connection -> {
            handoffs.insert(connection, handoff);
            return null;
        });

        assertEquals(handoff, database.inTransaction(connection ->
                handoffs.findById(connection, handoff.handoffId()).orElseThrow()));
        assertTrue(database.inTransactionBoolean(connection ->
                handoffs.acknowledge(connection, handoff.handoffId(), NOW.plusSeconds(60))));
        assertFalse(database.inTransactionBoolean(connection ->
                handoffs.acknowledge(connection, handoff.handoffId(), NOW.plusSeconds(120))));
        assertFalse(database.inTransactionBoolean(connection ->
                handoffs.reverse(
                        connection, handoff.handoffId(), "Too late", NOW.plusSeconds(180))));
    }

    @Test
    void unacknowledgedHandoffCanBeReversedAndRestoresCanonicalEvidenceState() {
        HandoffRecord handoff = handoff();
        database.inTransaction(connection -> {
            handoffs.insert(connection, handoff);
            return null;
        });

        assertEquals(handoff, database.inTransaction(connection ->
                handoffs.findUnacknowledgedForRequest(
                        connection, handoff.requestId()).orElseThrow()));
        assertTrue(database.inTransactionBoolean(connection ->
                handoffs.reverse(
                        connection,
                        handoff.handoffId(),
                        "Collection cancelled",
                        NOW.plusSeconds(60))));
        assertTrue(database.inTransactionBoolean(connection ->
                handoffs.findUnacknowledgedForRequest(
                        connection, handoff.requestId()).isEmpty()));
        assertEquals(EvidenceCustodyState.IN_STORAGE, evidenceState());
    }

    @Test
    void checkoutReturnMustBeInitiatedBeforeCompletion() {
        HandoffRecord handoff = handoff();
        CheckoutRecord checkout = checkout();
        database.inTransaction(connection -> {
            handoffs.insert(connection, handoff);
            assertTrue(handoffs.acknowledge(
                    connection, handoff.handoffId(), NOW.plusSeconds(30)));
            checkouts.insert(connection, checkout);
            return null;
        });

        assertTrue(database.inTransactionBoolean(connection ->
                checkouts.findActiveForEvidence(connection, checkout.evidenceId()).isPresent()));
        assertFalse(database.inTransactionBoolean(connection ->
                checkouts.complete(connection, checkout.checkoutId(), NOW.plusSeconds(60))));
        assertTrue(database.inTransactionBoolean(connection ->
                checkouts.markReturnInitiated(
                        connection, checkout.checkoutId(), NOW.plusSeconds(60))));
        assertEquals(EvidenceCustodyState.HANDIN_AWAITING_ACK,
                database.inTransaction(connection -> checkouts.findById(
                        connection, checkout.checkoutId()).orElseThrow().evidenceState()));
        assertTrue(database.inTransactionBoolean(connection ->
                checkouts.complete(connection, checkout.checkoutId(), NOW.plusSeconds(120))));
        assertEquals(EvidenceCustodyState.IN_STORAGE,
                database.inTransaction(connection -> checkouts.findById(
                        connection, checkout.checkoutId()).orElseThrow().evidenceState()));
        assertTrue(database.inTransactionBoolean(connection ->
                checkouts.findActiveForEvidence(connection, checkout.evidenceId()).isEmpty()));
    }

    @Test
    void failedCustodyPreconditionRollsBackTheInsertedHandoff() {
        database.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "UPDATE evidence_item SET custody_state = ? WHERE id = ?")) {
                statement.setString(1, EvidenceCustodyState.CHECKED_OUT.name());
                statement.setString(2, EVIDENCE_ID.toString());
                statement.executeUpdate();
                return null;
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });

        assertThrows(RepositoryException.Conflict.class, () ->
                database.inTransaction(connection -> {
                    handoffs.insert(connection, handoff());
                    return null;
                }));

        assertTrue(database.inTransactionBoolean(connection ->
                handoffs.findById(connection, HANDOFF_ID).isEmpty()));
        assertEquals(EvidenceCustodyState.CHECKED_OUT, evidenceState());
    }

    private void insertApprovedRequest() {
        CheckoutRequestRecord request = new CheckoutRequestRecord(
                REQUEST_ID,
                EVIDENCE_ID,
                CheckoutRepositoryTestDatabase.INVESTIGATOR_ID,
                "Review item",
                NOW.plusSeconds(3600),
                CheckoutRequestStatus.PENDING,
                NOW);
        database.inTransaction(connection -> {
            requests.insertPending(connection, request);
            assertTrue(requests.transitionStatus(
                    connection,
                    REQUEST_ID,
                    CheckoutRequestStatus.PENDING,
                    CheckoutRequestStatus.APPROVED));
            return null;
        });
    }

    private EvidenceCustodyState evidenceState() {
        return database.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT custody_state FROM evidence_item WHERE id = ?")) {
                statement.setString(1, EVIDENCE_ID.toString());
                try (var results = statement.executeQuery()) {
                    return EvidenceCustodyState.valueOf(results.getString(1));
                }
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static HandoffRecord handoff() {
        return new HandoffRecord(
                HANDOFF_ID,
                REQUEST_ID,
                EVIDENCE_ID,
                CheckoutRepositoryTestDatabase.CUSTODIAN_ID,
                NOW,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static CheckoutRecord checkout() {
        return new CheckoutRecord(
                CHECKOUT_ID,
                REQUEST_ID,
                EVIDENCE_ID,
                CheckoutRepositoryTestDatabase.INVESTIGATOR_ID,
                NOW.plusSeconds(30),
                Optional.empty(),
                Optional.empty(),
                EvidenceCustodyState.CHECKED_OUT);
    }
}
