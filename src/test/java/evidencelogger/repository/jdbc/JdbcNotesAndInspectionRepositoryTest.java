package evidencelogger.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.ReturnInspectionOutcome;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutRecord;
import evidencelogger.repository.checkout.CheckoutRequestRecord;
import evidencelogger.repository.checkout.ExaminationNoteRecord;
import evidencelogger.repository.checkout.HandoffRecord;
import evidencelogger.repository.checkout.NoteCorrectionRecord;
import evidencelogger.repository.checkout.ReturnInspectionRecord;

class JdbcNotesAndInspectionRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");
    private static final EvidenceId EVIDENCE_ID =
            EvidenceId.parse("00000000-0000-0000-0000-000000000530");
    private static final CheckoutRequestId REQUEST_ID =
            CheckoutRequestId.parse("00000000-0000-0000-0000-000000000531");
    private static final HandoffId HANDOFF_ID =
            HandoffId.parse("00000000-0000-0000-0000-000000000532");
    private static final CheckoutId CHECKOUT_ID =
            CheckoutId.parse("00000000-0000-0000-0000-000000000533");
    private static final ExaminationNoteId FIRST_NOTE_ID =
            ExaminationNoteId.parse("00000000-0000-0000-0000-000000000534");
    private static final ExaminationNoteId SECOND_NOTE_ID =
            ExaminationNoteId.parse("00000000-0000-0000-0000-000000000535");

    @TempDir
    Path temporaryDirectory;

    private CheckoutRepositoryTestDatabase database;
    private JdbcExaminationNoteRepository notes;
    private JdbcReturnInspectionRepository inspections;

    @BeforeEach
    void setUp() {
        database = new CheckoutRepositoryTestDatabase(
                temporaryDirectory.resolve("notes-inspection.db"));
        database.insertEvidence(EVIDENCE_ID);
        createActiveCheckout();
        notes = new JdbcExaminationNoteRepository();
        inspections = new JdbcReturnInspectionRepository();
    }

    @Test
    void notesAndCorrectionsRemainAppendOnlyAndOrderedOnProductionSchema() {
        ExaminationNoteRecord first = new ExaminationNoteRecord(
                FIRST_NOTE_ID,
                CHECKOUT_ID,
                CheckoutRepositoryTestDatabase.INVESTIGATOR_ID,
                "First observation",
                NOW);
        ExaminationNoteRecord second = new ExaminationNoteRecord(
                SECOND_NOTE_ID,
                CHECKOUT_ID,
                CheckoutRepositoryTestDatabase.INVESTIGATOR_ID,
                "Second observation",
                NOW.plusSeconds(1));
        NoteCorrectionRecord correction = new NoteCorrectionRecord(
                FIRST_NOTE_ID,
                first.authorId(),
                "Corrected observation",
                "Typo",
                NOW.plusSeconds(2));

        database.inTransaction(connection -> {
            notes.insert(connection, first);
            notes.insert(connection, second);
            notes.appendCorrection(connection, correction);
            return null;
        });

        assertEquals(List.of(first, second), database.inTransaction(connection ->
                notes.findForCheckout(connection, CHECKOUT_ID)));
        assertEquals(Optional.of(first), database.inTransaction(connection ->
                notes.findById(connection, FIRST_NOTE_ID)));
        assertEquals(List.of(correction), database.inTransaction(connection ->
                notes.findCorrections(connection, FIRST_NOTE_ID)));
    }

    @Test
    void duplicateInspectionBecomesAConflict() {
        ReturnInspectionRecord inspection = new ReturnInspectionRecord(
                CHECKOUT_ID,
                CheckoutRepositoryTestDatabase.CUSTODIAN_ID,
                ReturnInspectionOutcome.STORED,
                false,
                "",
                NOW.plusSeconds(120));
        database.inTransaction(connection -> {
            inspections.insert(connection, inspection);
            return null;
        });

        Executable duplicateInspection = () -> database.inTransaction(connection -> {
            inspections.insert(connection, inspection);
            return null;
        });
        RepositoryException.Conflict conflict = assertThrows(
                RepositoryException.Conflict.class, duplicateInspection);

        assertEquals("return inspection conflicts with an existing record", conflict.getMessage());
        assertEquals(Optional.of(inspection), database.inTransaction(connection ->
                inspections.findByCheckout(connection, CHECKOUT_ID)));
    }

    private void createActiveCheckout() {
        JdbcCheckoutRequestRepository requests = new JdbcCheckoutRequestRepository();
        JdbcHandoffRepository handoffs = new JdbcHandoffRepository();
        JdbcCheckoutRepository checkouts = new JdbcCheckoutRepository();
        CheckoutRequestRecord request = new CheckoutRequestRecord(
                REQUEST_ID,
                EVIDENCE_ID,
                CheckoutRepositoryTestDatabase.INVESTIGATOR_ID,
                "Review item",
                NOW.plusSeconds(3600),
                CheckoutRequestStatus.PENDING,
                NOW);
        HandoffRecord handoff = new HandoffRecord(
                HANDOFF_ID,
                REQUEST_ID,
                EVIDENCE_ID,
                CheckoutRepositoryTestDatabase.CUSTODIAN_ID,
                NOW.plusSeconds(10),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        CheckoutRecord checkout = new CheckoutRecord(
                CHECKOUT_ID,
                REQUEST_ID,
                EVIDENCE_ID,
                CheckoutRepositoryTestDatabase.INVESTIGATOR_ID,
                NOW.plusSeconds(20),
                Optional.empty(),
                Optional.empty(),
                EvidenceCustodyState.CHECKED_OUT);

        database.inTransaction(connection -> {
            requests.insertPending(connection, request);
            requests.transitionStatus(
                    connection,
                    REQUEST_ID,
                    CheckoutRequestStatus.PENDING,
                    CheckoutRequestStatus.APPROVED);
            handoffs.insert(connection, handoff);
            handoffs.acknowledge(connection, HANDOFF_ID, NOW.plusSeconds(20));
            checkouts.insert(connection, checkout);
            return null;
        });
    }
}
