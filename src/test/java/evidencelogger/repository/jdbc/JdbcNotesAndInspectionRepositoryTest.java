package evidencelogger.repository.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.ReturnInspectionOutcome;
import evidencelogger.domain.UserId;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.ExaminationNoteRecord;
import evidencelogger.repository.checkout.NoteCorrectionRecord;
import evidencelogger.repository.checkout.ReturnInspectionRecord;

class JdbcNotesAndInspectionRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

    private Connection connection;
    private JdbcExaminationNoteRepository notes;
    private JdbcReturnInspectionRepository inspections;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE examination_note ("
                    + "note_id TEXT PRIMARY KEY, checkout_id TEXT NOT NULL, "
                    + "author_id TEXT NOT NULL, text TEXT NOT NULL, created_at TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE note_correction ("
                    + "note_id TEXT NOT NULL, author_id TEXT NOT NULL, "
                    + "correction_text TEXT NOT NULL, reason TEXT NOT NULL, "
                    + "created_at TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE return_inspection ("
                    + "checkout_id TEXT PRIMARY KEY, custodian_id TEXT NOT NULL, "
                    + "outcome TEXT NOT NULL, unplanned INTEGER NOT NULL, reason TEXT NOT NULL, "
                    + "inspected_at TEXT NOT NULL)");
        }
        notes = new JdbcExaminationNoteRepository();
        inspections = new JdbcReturnInspectionRepository();
    }

    @Test
    void notesAndCorrectionsRemainAppendOnlyAndOrdered() {
        CheckoutId checkoutId = new CheckoutId(UUID.randomUUID());
        ExaminationNoteId firstId = new ExaminationNoteId(UUID.randomUUID());
        ExaminationNoteId secondId = new ExaminationNoteId(UUID.randomUUID());
        ExaminationNoteRecord first = new ExaminationNoteRecord(
                firstId, checkoutId, new UserId(UUID.randomUUID()), "First observation", NOW);
        ExaminationNoteRecord second = new ExaminationNoteRecord(
                secondId,
                checkoutId,
                new UserId(UUID.randomUUID()),
                "Second observation",
                NOW.plusSeconds(1));
        NoteCorrectionRecord correction = new NoteCorrectionRecord(
                firstId,
                first.authorId(),
                "Corrected observation",
                "Typo",
                NOW.plusSeconds(2));

        notes.insert(connection, first);
        notes.insert(connection, second);
        notes.appendCorrection(connection, correction);

        assertEquals(List.of(first, second), notes.findForCheckout(connection, checkoutId));
        assertEquals(Optional.of(first), notes.findById(connection, firstId));
        assertEquals(List.of(correction), notes.findCorrections(connection, firstId));
    }

    @Test
    void duplicateInspectionBecomesAConflict() {
        CheckoutId checkoutId = new CheckoutId(UUID.randomUUID());
        ReturnInspectionRecord inspection = new ReturnInspectionRecord(
                checkoutId,
                new UserId(UUID.randomUUID()),
                ReturnInspectionOutcome.STORED,
                false,
                "",
                NOW);
        inspections.insert(connection, inspection);

        Executable duplicateInspection = () -> inspections.insert(connection, inspection);
        RepositoryException.Conflict conflict = assertThrows(
                RepositoryException.Conflict.class, duplicateInspection);

        assertEquals("return inspection conflicts with an existing record", conflict.getMessage());
        assertEquals(Optional.of(inspection), inspections.findByCheckout(connection, checkoutId));
    }
}
