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

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.ConnectionFactory;
import evidencelogger.infrastructure.db.JdbcTransactionRunner;
import evidencelogger.infrastructure.db.MigrationRunner;
import evidencelogger.infrastructure.db.SqliteConnectionFactory;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.repository.history.AuditEventReadRepository;

class JdbcAuditEventReadRepositoryTest {
    private static final Instant EVENT_TIME = Instant.parse("2026-09-25T08:00:00Z");
    private static final UserId ALEX_ID = UserId.parse("00000000-0000-0000-0000-000000000002");
    private static final CaseId ASSIGNED_CASE_ID =
            CaseId.parse("00000000-0000-0000-0000-000000000710");
    private static final CaseId UNASSIGNED_CASE_ID =
            CaseId.parse("00000000-0000-0000-0000-000000000720");
    private static final AuditEventId FIRST_EVENT_ID =
            AuditEventId.parse("00000000-0000-0000-0000-000000000711");
    private static final AuditEventId SECOND_EVENT_ID =
            AuditEventId.parse("00000000-0000-0000-0000-000000000712");
    private static final StorageLocationId LOCATION_ID = StorageLocationId.parse(
            "00000000-0000-0000-0000-000000000730");
    private static final EvidenceId EVIDENCE_ID = EvidenceId.parse(
            "00000000-0000-0000-0000-000000000731");
    private static final CheckoutRequestId REQUEST_ID = CheckoutRequestId.parse(
            "00000000-0000-0000-0000-000000000732");

    @TempDir
    Path temporaryDirectory;

    private TransactionRunner transactions;
    private AuditEventReadRepository reads;

    @BeforeEach
    void setUp() {
        ConnectionFactory connections = new SqliteConnectionFactory(
                temporaryDirectory.resolve("audit-history.db"));
        new MigrationRunner(connections, Clock.fixed(EVENT_TIME, ZoneOffset.UTC)).migrate();
        transactions = new JdbcTransactionRunner(connections);
        seedFixture();
        reads = new JdbcAuditEventReadRepository();
    }

    @Test
    void scopesHistoryToCurrentAssignmentAndOrdersEqualTimestampsByStableEventId() {
        List<AuditEventReadRepository.EventDetails> events = transactions.inTransaction(connection ->
                reads.listEventsForCase(connection, ASSIGNED_CASE_ID, Optional.of(ALEX_ID)));

        assertEquals(List.of(FIRST_EVENT_ID, SECOND_EVENT_ID), events.stream()
                .map(AuditEventReadRepository.EventDetails::eventId)
                .toList());
        assertEquals(AuditEventType.REQUEST_REJECTED, events.getFirst().type());
        assertEquals(Optional.of(EVIDENCE_ID), events.getFirst().evidenceId());
        assertEquals(Optional.of("EV-HISTORY-001"), events.getFirst().evidenceReference());
        assertEquals(Optional.of(REQUEST_ID), events.getFirst().requestId());
        assertEquals(Optional.of(CheckoutRequestStatus.PENDING),
                events.getFirst().previousRequestStatus());
        assertEquals(Optional.of(CheckoutRequestStatus.REJECTED),
                events.getFirst().resultingRequestStatus());
        assertEquals(Optional.of(EvidenceCustodyState.IN_STORAGE),
                events.getFirst().previousCustodyState());
        assertEquals(Optional.of(EvidenceCustodyState.IN_STORAGE),
                events.getFirst().resultingCustodyState());
        assertEquals("Insufficient purpose", events.getFirst().reason().orElseThrow());
        assertEquals(AuditEventType.EXAMINATION_NOTE_CORRECTED, events.getLast().type());
        assertEquals("Corrected identifier", events.getLast().correctionText().orElseThrow());
        assertEquals(Optional.of(FIRST_EVENT_ID), events.getLast().correctedEventId());
        assertEquals(List.of(), transactions.inTransaction(connection -> reads.listEventsForCase(
                connection, UNASSIGNED_CASE_ID, Optional.of(ALEX_ID))));

        Optional<AuditEventReadRepository.EventSubjects> subjects = transactions.inTransaction(
                connection -> reads.findEventSubjects(connection, FIRST_EVENT_ID));
        assertTrue(subjects.isPresent());
        assertEquals(Optional.of(ASSIGNED_CASE_ID), subjects.orElseThrow().caseId());

        transactions.inTransaction(connection -> {
            update(connection, "DELETE FROM case_assignment WHERE case_id = ? AND investigator_id = ?",
                    ASSIGNED_CASE_ID.toString(), ALEX_ID.toString());
            return null;
        });
        assertEquals(List.of(), transactions.inTransaction(connection -> reads.listEventsForCase(
                connection, ASSIGNED_CASE_ID, Optional.of(ALEX_ID))));
    }

    @Test
    void returnsEmptySubjectsForMissingCorrectionTarget() {
        Optional<AuditEventReadRepository.EventSubjects> subjects = transactions.inTransaction(
                connection -> reads.findEventSubjects(
                        connection,
                        AuditEventId.parse("00000000-0000-0000-0000-000000000799")));

        assertEquals(Optional.empty(), subjects);
    }

    private void seedFixture() {
        transactions.inTransaction(connection -> {
            update(connection, "INSERT INTO case_record(id, title, created_at) VALUES (?, ?, ?)",
                    ASSIGNED_CASE_ID.toString(), "Assigned case", EVENT_TIME.toString());
            update(connection, "INSERT INTO case_record(id, title, created_at) VALUES (?, ?, ?)",
                    UNASSIGNED_CASE_ID.toString(), "Unassigned case", EVENT_TIME.toString());
            update(connection, "INSERT INTO case_assignment(case_id, investigator_id, assigned_at)"
                    + " VALUES (?, ?, ?)", ASSIGNED_CASE_ID.toString(), ALEX_ID.toString(),
                    EVENT_TIME.toString());
            update(connection, "INSERT INTO storage_location(id, name, created_at) VALUES (?, ?, ?)",
                    LOCATION_ID.toString(), "History locker", EVENT_TIME.toString());
            update(connection, "INSERT INTO evidence_item("
                    + "id, case_id, public_reference, description, storage_location_id, "
                    + "custody_state, registered_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    EVIDENCE_ID.toString(), ASSIGNED_CASE_ID.toString(), "EV-HISTORY-001",
                    "History fixture", LOCATION_ID.toString(), "IN_STORAGE", EVENT_TIME.toString());
            update(connection, "INSERT INTO checkout_request("
                    + "id, evidence_id, requester_id, purpose, expected_return_at, status, "
                    + "requested_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    REQUEST_ID.toString(), EVIDENCE_ID.toString(), ALEX_ID.toString(),
                    "History fixture", EVENT_TIME.plusSeconds(3600).toString(),
                    "REJECTED", EVENT_TIME.toString());
            insertEvent(connection, FIRST_EVENT_ID, AuditEventType.REQUEST_REJECTED,
                    ASSIGNED_CASE_ID, null, "Insufficient purpose");
            update(connection, "UPDATE audit_event SET evidence_id = ?, request_id = ?, "
                    + "previous_request_status = ?, resulting_request_status = ?, "
                    + "previous_custody_state = ?, resulting_custody_state = ? WHERE id = ?",
                    EVIDENCE_ID.toString(), REQUEST_ID.toString(), "PENDING", "REJECTED",
                    "IN_STORAGE", "IN_STORAGE", FIRST_EVENT_ID.toString());
            insertEvent(connection, SECOND_EVENT_ID, AuditEventType.EXAMINATION_NOTE_CORRECTED,
                    ASSIGNED_CASE_ID, "Corrected identifier", null);
            update(connection, "UPDATE audit_event SET corrected_event_id = ? WHERE id = ?",
                    FIRST_EVENT_ID.toString(), SECOND_EVENT_ID.toString());
            insertEvent(connection, AuditEventId.parse("00000000-0000-0000-0000-000000000721"),
                    AuditEventType.REQUEST_REJECTED, UNASSIGNED_CASE_ID, null, "Private case");
            return null;
        });
    }

    private static void insertEvent(
            Connection connection,
            AuditEventId eventId,
            AuditEventType type,
            CaseId caseId,
            String correctionText,
            String reason) {
        update(connection, "INSERT INTO audit_event("
                + "id, event_type, actor_id, actor_role, event_time, case_id, correction_text, reason)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)", eventId.toString(), type.name(),
                "00000000-0000-0000-0000-000000000001", "EVIDENCE_CUSTODIAN",
                EVENT_TIME.toString(), caseId.toString(), correctionText, reason);
    }

    private static void update(Connection connection, String sql, String... values) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new AssertionError("Unable to seed audit-history fixture", exception);
        }
    }
}
