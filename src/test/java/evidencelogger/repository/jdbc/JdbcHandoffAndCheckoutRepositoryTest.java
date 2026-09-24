package evidencelogger.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
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
import evidencelogger.domain.ReturnInspectionOutcome;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutRecord;
import evidencelogger.repository.checkout.CheckoutRequestRecord;
import evidencelogger.repository.checkout.HandoffRecord;
import evidencelogger.repository.checkout.ReturnInspectionRecord;

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
    private static final EvidenceId OTHER_EVIDENCE_ID =
            EvidenceId.parse("00000000-0000-0000-0000-000000000524");

    @TempDir
    Path temporaryDirectory;

    private CheckoutRepositoryTestDatabase database;
    private JdbcCheckoutRequestRepository requests;
    private JdbcHandoffRepository handoffs;
    private JdbcCheckoutRepository checkouts;
    private JdbcReturnInspectionRepository inspections;

    @BeforeEach
    void setUp() {
        database = new CheckoutRepositoryTestDatabase(
                temporaryDirectory.resolve("handoff-checkout.db"));
        database.insertEvidence(EVIDENCE_ID);
        requests = new JdbcCheckoutRequestRepository();
        handoffs = new JdbcHandoffRepository();
        checkouts = new JdbcCheckoutRepository();
        inspections = new JdbcReturnInspectionRepository();
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
        assertEquals(CheckoutRequestStatus.CANCELLED, requestStatus());
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
        assertFalse(database.inTransactionBoolean(connection ->
                checkouts.complete(connection, checkout.checkoutId(), NOW.plusSeconds(120))));
        assertTrue(database.inTransactionBoolean(connection ->
                insertInspectionAndComplete(
                        connection, false, "", NOW.plusSeconds(120))));
        assertEquals(EvidenceCustodyState.IN_STORAGE,
                database.inTransaction(connection -> checkouts.findById(
                        connection, checkout.checkoutId()).orElseThrow().evidenceState()));
        assertTrue(database.inTransactionBoolean(connection ->
                checkouts.findActiveForEvidence(connection, checkout.evidenceId()).isEmpty()));
        assertEquals(CheckoutRequestStatus.CONSUMED, requestStatus());
    }

    @Test
    void unplannedReturnRequiresInspectionAndCompletesWithoutInitiation() {
        HandoffRecord handoff = handoff();
        CheckoutRecord checkout = checkout();
        database.inTransaction(connection -> {
            handoffs.insert(connection, handoff);
            assertTrue(handoffs.acknowledge(
                    connection, handoff.handoffId(), NOW.plusSeconds(30)));
            checkouts.insert(connection, checkout);
            return null;
        });

        assertFalse(database.inTransactionBoolean(connection ->
                checkouts.completeUnplanned(
                        connection, CHECKOUT_ID, NOW.plusSeconds(60))));
        assertTrue(database.inTransactionBoolean(connection ->
                insertInspectionAndComplete(
                        connection,
                        true,
                        "Returned without prior initiation",
                        NOW.plusSeconds(60))));

        CheckoutRecord completed = database.inTransaction(connection ->
                checkouts.findById(connection, CHECKOUT_ID).orElseThrow());
        assertEquals(Optional.of(NOW.plusSeconds(60)), completed.completedAt());
        assertEquals(EvidenceCustodyState.IN_STORAGE, completed.evidenceState());
    }

    @Test
    void handoffRequiresAnApprovedRequestForTheSameEvidence() {
        database.insertAdditionalEvidence(OTHER_EVIDENCE_ID);
        HandoffRecord mismatched = new HandoffRecord(
                HANDOFF_ID,
                REQUEST_ID,
                OTHER_EVIDENCE_ID,
                CheckoutRepositoryTestDatabase.CUSTODIAN_ID,
                NOW,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        assertThrows(RepositoryException.Conflict.class, () ->
                database.inTransaction(connection -> {
                    handoffs.insert(connection, mismatched);
                    return null;
                }));

        assertEquals(EvidenceCustodyState.IN_STORAGE, evidenceState(OTHER_EVIDENCE_ID));
        assertTrue(database.inTransactionBoolean(connection ->
                handoffs.findById(connection, HANDOFF_ID).isEmpty()));
    }

    @Test
    void handoffRejectsARequestThatIsNotApproved() {
        database.inTransaction(connection -> {
            assertTrue(requests.transitionStatus(
                    connection,
                    REQUEST_ID,
                    CheckoutRequestStatus.APPROVED,
                    CheckoutRequestStatus.PENDING));
            return null;
        });

        assertThrows(RepositoryException.Conflict.class, () ->
                database.inTransaction(connection -> {
                    handoffs.insert(connection, handoff());
                    return null;
                }));

        assertEquals(EvidenceCustodyState.IN_STORAGE, evidenceState());
    }

    @Test
    void handoffInsertRejectsAlreadyAcknowledgedOrReversedRecords() {
        HandoffRecord acknowledged = new HandoffRecord(
                HANDOFF_ID,
                REQUEST_ID,
                EVIDENCE_ID,
                CheckoutRepositoryTestDatabase.CUSTODIAN_ID,
                NOW,
                Optional.of(NOW.plusSeconds(1)),
                Optional.empty(),
                Optional.empty());
        HandoffRecord reversed = new HandoffRecord(
                HANDOFF_ID,
                REQUEST_ID,
                EVIDENCE_ID,
                CheckoutRepositoryTestDatabase.CUSTODIAN_ID,
                NOW,
                Optional.empty(),
                Optional.of(NOW.plusSeconds(1)),
                Optional.of("Already reversed"));

        assertThrows(IllegalArgumentException.class, () ->
                database.inTransaction(connection -> {
                    handoffs.insert(connection, acknowledged);
                    return null;
                }));
        assertThrows(IllegalArgumentException.class, () ->
                database.inTransaction(connection -> {
                    handoffs.insert(connection, reversed);
                    return null;
                }));

        assertTrue(database.inTransactionBoolean(connection ->
                handoffs.findById(connection, HANDOFF_ID).isEmpty()));
        assertEquals(EvidenceCustodyState.IN_STORAGE, evidenceState());
    }

    @Test
    void blankHandoffReversalReasonBecomesAConflictWithoutPartialChanges() {
        database.inTransaction(connection -> {
            handoffs.insert(connection, handoff());
            return null;
        });

        assertThrows(RepositoryException.Conflict.class, () ->
                database.inTransaction(connection -> {
                    handoffs.reverse(connection, HANDOFF_ID, " ", NOW.plusSeconds(60));
                    return null;
                }));

        assertTrue(database.inTransactionBoolean(connection ->
                handoffs.findById(connection, HANDOFF_ID)
                        .orElseThrow()
                        .reversedAt()
                        .isEmpty()));
        assertEquals(CheckoutRequestStatus.APPROVED, requestStatus());
        assertEquals(EvidenceCustodyState.HANDOFF_AWAITING_ACK, evidenceState());
    }

    @Test
    void acknowledgmentRejectsStaleEvidenceCustodyState() {
        database.inTransaction(connection -> {
            handoffs.insert(connection, handoff());
            setEvidenceState(connection, EVIDENCE_ID, EvidenceCustodyState.IN_STORAGE);
            assertFalse(handoffs.acknowledge(
                    connection, HANDOFF_ID, NOW.plusSeconds(60)));
            return null;
        });

        assertTrue(database.inTransactionBoolean(connection ->
                handoffs.findById(connection, HANDOFF_ID)
                        .orElseThrow()
                        .acknowledgedAt()
                        .isEmpty()));
    }

    @Test
    void failedCheckoutStateTransitionRollsBackRequestConsumptionAndCheckout() {
        database.inTransaction(connection -> {
            handoffs.insert(connection, handoff());
            assertTrue(handoffs.acknowledge(
                    connection, HANDOFF_ID, NOW.plusSeconds(30)));
            return null;
        });
        database.inTransaction(connection -> {
            setEvidenceState(connection, EVIDENCE_ID, EvidenceCustodyState.IN_STORAGE);
            return null;
        });

        assertThrows(RepositoryException.Conflict.class, () ->
                database.inTransaction(connection -> {
                    checkouts.insert(connection, checkout());
                    return null;
                }));

        assertEquals(CheckoutRequestStatus.APPROVED, requestStatus());
        assertEquals(EvidenceCustodyState.IN_STORAGE, evidenceState());
        assertTrue(database.inTransactionBoolean(connection ->
                checkouts.findById(connection, CHECKOUT_ID).isEmpty()));
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
        return evidenceState(EVIDENCE_ID);
    }

    private EvidenceCustodyState evidenceState(EvidenceId evidenceId) {
        return database.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT custody_state FROM evidence_item WHERE id = ?")) {
                statement.setString(1, evidenceId.toString());
                try (var results = statement.executeQuery()) {
                    return EvidenceCustodyState.valueOf(results.getString(1));
                }
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private CheckoutRequestStatus requestStatus() {
        return database.inTransaction(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT status FROM checkout_request WHERE id = ?")) {
                statement.setString(1, REQUEST_ID.toString());
                try (var results = statement.executeQuery()) {
                    return CheckoutRequestStatus.valueOf(results.getString(1));
                }
            } catch (java.sql.SQLException exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private boolean insertInspectionAndComplete(
            Connection connection, boolean unplanned, String reason, Instant inspectedAt) {
        inspections.insert(connection, new ReturnInspectionRecord(
                CHECKOUT_ID,
                CheckoutRepositoryTestDatabase.CUSTODIAN_ID,
                ReturnInspectionOutcome.STORED,
                unplanned,
                reason,
                inspectedAt));
        return unplanned
                ? checkouts.completeUnplanned(connection, CHECKOUT_ID, inspectedAt)
                : checkouts.complete(connection, CHECKOUT_ID, inspectedAt);
    }

    private static void setEvidenceState(
            Connection connection,
            EvidenceId evidenceId,
            EvidenceCustodyState state) {
        try (var statement = connection.prepareStatement(
                "UPDATE evidence_item SET custody_state = ? WHERE id = ?")) {
            statement.setString(1, state.name());
            statement.setString(2, evidenceId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
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
