package evidencelogger.repository.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.ReturnInspectionOutcome;
import evidencelogger.domain.UserId;

class CheckoutPersistenceContractsTest {
    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

    @Test
    void recordsPreserveTypedRelationshipsAndImmutableWorkflowData() {
        CheckoutRequestId requestId = new CheckoutRequestId(UUID.randomUUID());
        EvidenceId evidenceId = new EvidenceId(UUID.randomUUID());
        UserId investigatorId = new UserId(UUID.randomUUID());
        CheckoutRequestRecord request = new CheckoutRequestRecord(
                requestId,
                evidenceId,
                investigatorId,
                "Review item",
                NOW.plusSeconds(3600),
                CheckoutRequestStatus.PENDING,
                NOW);

        CheckoutId checkoutId = new CheckoutId(UUID.randomUUID());
        CheckoutRecord checkout = new CheckoutRecord(
                checkoutId,
                requestId,
                evidenceId,
                investigatorId,
                NOW,
                Optional.of(NOW.plusSeconds(600)),
                Optional.empty(),
                EvidenceCustodyState.HANDIN_AWAITING_ACK);

        assertEquals(requestId, request.requestId());
        assertEquals(evidenceId, checkout.evidenceId());
        assertEquals(EvidenceCustodyState.HANDIN_AWAITING_ACK, checkout.evidenceState());
    }

    @Test
    void appendOnlyRecordsRequireTheirTargetRelationships() {
        ExaminationNoteId noteId = new ExaminationNoteId(UUID.randomUUID());
        CheckoutId checkoutId = new CheckoutId(UUID.randomUUID());
        UserId authorId = new UserId(UUID.randomUUID());

        assertThrows(NullPointerException.class, () ->
                new ExaminationNoteRecord(noteId, checkoutId, null, "Observation", NOW));
        assertThrows(NullPointerException.class, () ->
                new NoteCorrectionRecord(noteId, authorId, "Correction", null, NOW));
    }

    @Test
    void handoffAndInspectionRecordsKeepActorAndTimeData() {
        HandoffRecord handoff = new HandoffRecord(
                new HandoffId(UUID.randomUUID()),
                new CheckoutRequestId(UUID.randomUUID()),
                new EvidenceId(UUID.randomUUID()),
                new UserId(UUID.randomUUID()),
                NOW,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        ReturnInspectionRecord inspection = new ReturnInspectionRecord(
                new CheckoutId(UUID.randomUUID()),
                new UserId(UUID.randomUUID()),
                ReturnInspectionOutcome.STORED,
                true,
                "Unexpected delivery",
                NOW);

        assertEquals(Optional.empty(), handoff.acknowledgedAt());
        assertEquals(ReturnInspectionOutcome.STORED, inspection.outcome());
        assertEquals("Unexpected delivery", inspection.reason());
    }
}
