package evidencelogger.service.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.ReturnInspectionOutcome;
import evidencelogger.domain.UserId;

class CheckoutContractsTest {
    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

    @Test
    void submitRequestRejectsMissingRequiredInput() {
        EvidenceId evidenceId = new EvidenceId(UUID.randomUUID());

        assertThrows(NullPointerException.class, () ->
                new CheckoutCommands.SubmitRequest(null, "Review item", NOW));
        assertThrows(NullPointerException.class, () ->
                new CheckoutCommands.SubmitRequest(evidenceId, null, NOW));
        assertThrows(NullPointerException.class, () ->
                new CheckoutCommands.SubmitRequest(evidenceId, "Review item", null));
    }

    @Test
    void inspectionUsesTheOnlySupportedOutcome() {
        CheckoutId checkoutId = new CheckoutId(UUID.randomUUID());
        CheckoutCommands.InspectReturn command = new CheckoutCommands.InspectReturn(
                checkoutId, ReturnInspectionOutcome.STORED);

        assertEquals(ReturnInspectionOutcome.STORED, command.outcome());
    }

    @Test
    void commandsRejectBlankRequiredText() {
        EvidenceId evidenceId = new EvidenceId(UUID.randomUUID());
        CheckoutRequestId requestId = new CheckoutRequestId(UUID.randomUUID());
        HandoffId handoffId = new HandoffId(UUID.randomUUID());
        CheckoutId checkoutId = new CheckoutId(UUID.randomUUID());
        ExaminationNoteId noteId = new ExaminationNoteId(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () ->
                new CheckoutCommands.SubmitRequest(evidenceId, "  ", NOW));
        assertThrows(IllegalArgumentException.class, () ->
                new CheckoutCommands.CancelApprovedRequest(requestId, "\t"));
        assertThrows(IllegalArgumentException.class, () ->
                new CheckoutCommands.ReverseHandoff(handoffId, "\n"));
        assertThrows(IllegalArgumentException.class, () ->
                new CheckoutCommands.AddExaminationNote(checkoutId, "  "));
        assertThrows(IllegalArgumentException.class, () ->
                new CheckoutCommands.CorrectExaminationNote(noteId, "", "Reason"));
        assertThrows(IllegalArgumentException.class, () ->
                new CheckoutCommands.CorrectExaminationNote(noteId, "Correction", "  "));
        assertThrows(IllegalArgumentException.class, () ->
                new CheckoutCommands.InspectUnplannedReturn(
                        checkoutId, ReturnInspectionOutcome.STORED, "  "));
    }

    @Test
    void noteViewDefensivelyCopiesCorrections() {
        UserId authorId = new UserId(UUID.randomUUID());
        List<CheckoutViews.NoteCorrection> corrections = new ArrayList<>();
        corrections.add(new CheckoutViews.NoteCorrection(
                authorId, "Demo Investigator", "Corrected text", "Typing error", NOW));

        CheckoutViews.ExaminationNote note = new CheckoutViews.ExaminationNote(
                new ExaminationNoteId(UUID.randomUUID()),
                new CheckoutId(UUID.randomUUID()),
                authorId,
                "Demo Investigator",
                "Original text",
                NOW,
                corrections);
        corrections.clear();

        assertEquals(1, note.corrections().size());
        assertThrows(UnsupportedOperationException.class, () -> note.corrections().clear());
    }

    @Test
    void identifiersRemainDistinctTypesInReadModels() {
        CaseId caseId = new CaseId(UUID.randomUUID());
        EvidenceId evidenceId = new EvidenceId(UUID.randomUUID());

        assertNotEquals(caseId, evidenceId);
        assertEquals(caseId.toString(), caseId.value().toString());
    }
}
