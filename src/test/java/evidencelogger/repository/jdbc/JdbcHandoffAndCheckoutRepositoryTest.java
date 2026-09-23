package evidencelogger.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.UserId;
import evidencelogger.repository.checkout.CheckoutRecord;
import evidencelogger.repository.checkout.HandoffRecord;

class JdbcHandoffAndCheckoutRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

    private Connection connection;
    private JdbcHandoffRepository handoffs;
    private JdbcCheckoutRepository checkouts;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE handoff ("
                    + "handoff_id TEXT PRIMARY KEY, request_id TEXT NOT NULL, "
                    + "evidence_id TEXT NOT NULL, custodian_id TEXT NOT NULL, "
                    + "recorded_at TEXT NOT NULL, acknowledged_at TEXT, reversed_at TEXT, "
                    + "reversal_reason TEXT)");
            statement.executeUpdate("CREATE TABLE checkout ("
                    + "checkout_id TEXT PRIMARY KEY, request_id TEXT NOT NULL, "
                    + "evidence_id TEXT NOT NULL, collector_id TEXT NOT NULL, "
                    + "collected_at TEXT NOT NULL, return_initiated_at TEXT, "
                    + "completed_at TEXT, evidence_state TEXT NOT NULL)");
        }
        handoffs = new JdbcHandoffRepository();
        checkouts = new JdbcCheckoutRepository();
    }

    @Test
    void handoffAcknowledgmentAndReversalAreConditional() {
        HandoffRecord handoff = handoff();
        handoffs.insert(connection, handoff);

        assertEquals(handoff, handoffs.findById(connection, handoff.handoffId()).orElseThrow());
        assertTrue(handoffs.acknowledge(connection, handoff.handoffId(), NOW.plusSeconds(60)));
        assertFalse(handoffs.acknowledge(connection, handoff.handoffId(), NOW.plusSeconds(120)));
        assertFalse(handoffs.reverse(
                connection, handoff.handoffId(), "Too late", NOW.plusSeconds(180)));
    }

    @Test
    void unacknowledgedHandoffCanBeReversedAndFoundByRequest() {
        HandoffRecord handoff = handoff();
        handoffs.insert(connection, handoff);

        assertEquals(handoff, handoffs.findUnacknowledgedForRequest(
                connection, handoff.requestId()).orElseThrow());
        assertTrue(handoffs.reverse(
                connection, handoff.handoffId(), "Collection cancelled", NOW.plusSeconds(60)));
        assertTrue(handoffs.findUnacknowledgedForRequest(
                connection, handoff.requestId()).isEmpty());
    }

    @Test
    void checkoutReturnMustBeInitiatedBeforeCompletion() {
        CheckoutRecord checkout = checkout();
        checkouts.insert(connection, checkout);

        assertTrue(checkouts.findActiveForEvidence(
                connection, checkout.evidenceId()).isPresent());
        assertFalse(checkouts.complete(connection, checkout.checkoutId(), NOW.plusSeconds(60)));
        assertTrue(checkouts.markReturnInitiated(
                connection, checkout.checkoutId(), NOW.plusSeconds(60)));
        assertEquals(EvidenceCustodyState.HANDIN_AWAITING_ACK,
                checkouts.findById(connection, checkout.checkoutId()).orElseThrow().evidenceState());
        assertTrue(checkouts.complete(connection, checkout.checkoutId(), NOW.plusSeconds(120)));
        assertEquals(EvidenceCustodyState.IN_STORAGE,
                checkouts.findById(connection, checkout.checkoutId()).orElseThrow().evidenceState());
        assertTrue(checkouts.findActiveForEvidence(
                connection, checkout.evidenceId()).isEmpty());
    }

    private static HandoffRecord handoff() {
        return new HandoffRecord(
                new HandoffId(UUID.randomUUID()),
                new CheckoutRequestId(UUID.randomUUID()),
                new EvidenceId(UUID.randomUUID()),
                new UserId(UUID.randomUUID()),
                NOW,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static CheckoutRecord checkout() {
        return new CheckoutRecord(
                new CheckoutId(UUID.randomUUID()),
                new CheckoutRequestId(UUID.randomUUID()),
                new EvidenceId(UUID.randomUUID()),
                new UserId(UUID.randomUUID()),
                NOW,
                Optional.empty(),
                Optional.empty(),
                EvidenceCustodyState.CHECKED_OUT);
    }
}
