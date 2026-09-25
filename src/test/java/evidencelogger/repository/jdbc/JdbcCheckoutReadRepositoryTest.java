package evidencelogger.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.ConnectionFactory;
import evidencelogger.infrastructure.db.JdbcTransactionRunner;
import evidencelogger.infrastructure.db.MigrationRunner;
import evidencelogger.infrastructure.db.SqliteConnectionFactory;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.repository.checkout.CheckoutReadRepository;

class JdbcCheckoutReadRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
    private static final UserId ALEX_ID = UserId.parse("00000000-0000-0000-0000-000000000002");
    private static final CaseId ASSIGNED_CASE_ID = CaseId.parse("00000000-0000-0000-0000-000000000610");
    private static final EvidenceId ASSIGNED_EVIDENCE_ID =
            EvidenceId.parse("00000000-0000-0000-0000-000000000611");
    private static final CheckoutRequestId ASSIGNED_REQUEST_ID =
            CheckoutRequestId.parse("00000000-0000-0000-0000-000000000612");
    private static final HandoffId ASSIGNED_HANDOFF_ID =
            HandoffId.parse("00000000-0000-0000-0000-000000000613");
    private static final CheckoutId ASSIGNED_CHECKOUT_ID =
            CheckoutId.parse("00000000-0000-0000-0000-000000000614");
    private static final ExaminationNoteId ASSIGNED_NOTE_ID =
            ExaminationNoteId.parse("00000000-0000-0000-0000-000000000615");
    private static final CaseId UNASSIGNED_CASE_ID =
            CaseId.parse("00000000-0000-0000-0000-000000000620");
    private static final EvidenceId UNASSIGNED_EVIDENCE_ID =
            EvidenceId.parse("00000000-0000-0000-0000-000000000621");
    private static final CheckoutRequestId UNASSIGNED_REQUEST_ID =
            CheckoutRequestId.parse("00000000-0000-0000-0000-000000000622");

    @TempDir
    Path temporaryDirectory;

    private TransactionRunner transactions;
    private CheckoutReadRepository reads;

    @BeforeEach
    void setUp() {
        ConnectionFactory connections = new SqliteConnectionFactory(
                temporaryDirectory.resolve("checkout-reads.db"));
        new MigrationRunner(connections, Clock.fixed(NOW, ZoneOffset.UTC)).migrate();
        transactions = new JdbcTransactionRunner(connections);
        seedFixture();
        reads = new JdbcCheckoutReadRepository();
    }

    @Test
    void joinsRequestCheckoutAndNoteDetailsAndScopesEveryReadToAssignedCases() {
        List<CheckoutReadRepository.RequestDetails> assignedRequests = transactions.inTransaction(
                connection -> reads.listRequests(
                        connection,
                        Optional.empty(),
                        Optional.of(ALEX_ID)));
        CheckoutReadRepository.RequestDetails request = assignedRequests.getFirst();

        assertEquals(List.of(ASSIGNED_REQUEST_ID), assignedRequests.stream()
                .map(CheckoutReadRepository.RequestDetails::requestId)
                .toList());
        assertEquals("Assigned case", request.caseTitle());
        assertEquals("EV-ASSIGNED", request.evidenceReference());
        assertEquals("Alex Investigator", request.requesterDisplayName());
        assertEquals(Optional.of(ASSIGNED_HANDOFF_ID), request.handoffId());
        assertEquals(Optional.of(ASSIGNED_CHECKOUT_ID), request.checkoutId());
        assertEquals(List.of(), transactions.inTransaction(connection -> reads.listRequests(
                connection,
                Optional.of(CheckoutRequestStatus.PENDING),
                Optional.of(ALEX_ID))));

        CheckoutReadRepository.CheckoutDetails checkout = transactions.inTransaction(connection ->
                reads.findCheckout(connection, ASSIGNED_CHECKOUT_ID, Optional.of(ALEX_ID))
                        .orElseThrow());
        assertEquals("Assigned case", checkout.caseTitle());
        assertEquals("Alex Investigator", checkout.collectorDisplayName());
        assertEquals(EvidenceCustodyState.CHECKED_OUT, checkout.evidenceState());

        CheckoutReadRepository.ExaminationNoteDetails note = transactions.inTransaction(connection ->
                reads.listNotes(connection, ASSIGNED_CHECKOUT_ID, Optional.of(ALEX_ID)).getFirst());
        assertEquals(ASSIGNED_NOTE_ID, note.noteId());
        assertEquals("Observed a sealed bag", note.text());
        assertEquals("Alex Investigator", note.authorDisplayName());
        assertEquals(1, note.corrections().size());
        assertEquals("Clarified seal number", note.corrections().getFirst().correctionText());

        assertTrue(transactions.inTransaction(connection -> reads.findRequest(
                connection, UNASSIGNED_REQUEST_ID, Optional.of(ALEX_ID))).isEmpty());
        assertTrue(transactions.inTransaction(connection -> reads.listRequestsForCase(
                connection, UNASSIGNED_CASE_ID, Optional.of(ALEX_ID))).isEmpty());
        assertTrue(transactions.inTransaction(connection -> reads.findCheckout(
                connection, ASSIGNED_CHECKOUT_ID, Optional.empty())).isPresent());

        transactions.inTransaction(connection -> {
            update(connection, "DELETE FROM case_assignment WHERE case_id = ? AND investigator_id = ?",
                    ASSIGNED_CASE_ID.toString(), ALEX_ID.toString());
            return null;
        });
        assertTrue(transactions.inTransaction(connection -> reads.findRequest(
                connection, ASSIGNED_REQUEST_ID, Optional.of(ALEX_ID))).isEmpty());
        assertTrue(transactions.inTransaction(connection -> reads.findCheckout(
                connection, ASSIGNED_CHECKOUT_ID, Optional.of(ALEX_ID))).isEmpty());
        assertEquals(List.of(), transactions.inTransaction(connection -> reads.listNotes(
                connection, ASSIGNED_CHECKOUT_ID, Optional.of(ALEX_ID))));
    }

    private void seedFixture() {
        transactions.inTransaction(connection -> {
            insertCase(connection, ASSIGNED_CASE_ID, "Assigned case");
            insertCase(connection, UNASSIGNED_CASE_ID, "Unassigned case");
            update(connection, """
                    INSERT INTO storage_location(id, name, created_at) VALUES (?, ?, ?)
                    """, "00000000-0000-0000-0000-000000000601", "Query test locker",
                    NOW.toString());
            update(connection, """
                    INSERT INTO case_assignment(case_id, investigator_id, assigned_at)
                    VALUES (?, ?, ?)
                    """, ASSIGNED_CASE_ID.toString(), ALEX_ID.toString(), NOW.toString());
            insertEvidence(connection, ASSIGNED_EVIDENCE_ID, ASSIGNED_CASE_ID, "EV-ASSIGNED");
            insertEvidence(connection, UNASSIGNED_EVIDENCE_ID, UNASSIGNED_CASE_ID, "EV-UNASSIGNED");
            insertRequest(connection, ASSIGNED_REQUEST_ID, ASSIGNED_EVIDENCE_ID,
                    CheckoutRequestStatus.CONSUMED);
            insertRequest(connection, UNASSIGNED_REQUEST_ID, UNASSIGNED_EVIDENCE_ID,
                    CheckoutRequestStatus.APPROVED);
            update(connection, """
                    INSERT INTO handoff(id, request_id, evidence_id, custodian_id, recorded_at, acknowledged_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, ASSIGNED_HANDOFF_ID.toString(), ASSIGNED_REQUEST_ID.toString(),
                    ASSIGNED_EVIDENCE_ID.toString(), "00000000-0000-0000-0000-000000000001",
                    NOW.toString(), NOW.plusSeconds(10).toString());
            update(connection, """
                    INSERT INTO checkout(id, handoff_id, request_id, evidence_id, collector_id, collected_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, ASSIGNED_CHECKOUT_ID.toString(), ASSIGNED_HANDOFF_ID.toString(),
                    ASSIGNED_REQUEST_ID.toString(), ASSIGNED_EVIDENCE_ID.toString(),
                    ALEX_ID.toString(), NOW.plusSeconds(10).toString());
            update(connection, """
                    INSERT INTO examination_note(id, checkout_id, author_id, note_text, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, ASSIGNED_NOTE_ID.toString(), ASSIGNED_CHECKOUT_ID.toString(),
                    ALEX_ID.toString(), "Observed a sealed bag", NOW.plusSeconds(20).toString());
            update(connection, """
                    INSERT INTO note_correction(id, note_id, author_id, correction_text, reason, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, "00000000-0000-0000-0000-000000000616", ASSIGNED_NOTE_ID.toString(),
                    ALEX_ID.toString(), "Clarified seal number", "Transcription correction",
                    NOW.plusSeconds(30).toString());
            return null;
        });
    }

    private static void insertCase(Connection connection, CaseId caseId, String title) {
        update(connection, "INSERT INTO case_record(id, title, created_at) VALUES (?, ?, ?)",
                caseId.toString(), title, NOW.toString());
    }

    private static void insertEvidence(
            Connection connection, EvidenceId evidenceId, CaseId caseId, String reference) {
        update(connection, """
                INSERT INTO evidence_item(
                    id, case_id, public_reference, description, storage_location_id, custody_state, registered_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, evidenceId.toString(), caseId.toString(), reference, "Evidence item",
                "00000000-0000-0000-0000-000000000601", EvidenceCustodyState.CHECKED_OUT.name(),
                NOW.toString());
    }

    private static void insertRequest(
            Connection connection,
            CheckoutRequestId requestId,
            EvidenceId evidenceId,
            CheckoutRequestStatus status) {
        update(connection, """
                INSERT INTO checkout_request(
                    id, evidence_id, requester_id, purpose, expected_return_at, status, requested_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, requestId.toString(), evidenceId.toString(), ALEX_ID.toString(), "Review evidence",
                NOW.plusSeconds(3600).toString(), status.name(), NOW.toString());
    }

    private static void update(Connection connection, String sql, String... values) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new AssertionError("Unable to seed checkout read fixture", exception);
        }
    }
}
