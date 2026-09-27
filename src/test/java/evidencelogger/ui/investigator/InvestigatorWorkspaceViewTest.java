package evidencelogger.ui.investigator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.Role;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.domain.UserId;
import evidencelogger.service.dto.CaseworkViews;
import evidencelogger.service.dto.CheckoutViews;
import evidencelogger.service.dto.HistoryViews;
import javafx.scene.paint.Color;

class InvestigatorWorkspaceViewTest {
    private static final Instant NOW = Instant.parse("2026-09-25T08:00:00Z");

    @Test
    void formatsAssignedCaseCreationTimeForTheCaseList() {
        Instant createdAt = Instant.parse("2026-09-26T17:05:45Z");
        assertEquals(InvestigatorWorkspaceView.formatTimestamp(
                createdAt, ZoneId.systemDefault()),
                InvestigatorWorkspaceView.formatCaseCreatedAt(createdAt));
    }

    @Test
    void formatsExpectedReturnTimeForTheRequestList() {
        Instant expectedReturnAt = Instant.parse("2026-09-26T17:05:45Z");
        assertEquals(InvestigatorWorkspaceView.formatTimestamp(
                expectedReturnAt, ZoneId.systemDefault()),
                InvestigatorWorkspaceView.formatRequestExpectedReturn(expectedReturnAt));
    }

    @Test
    void formatsCheckoutTimestampsForTheActiveCheckoutList() {
        Instant collectedAt = Instant.parse("2026-09-26T17:05:45Z");
        assertEquals(InvestigatorWorkspaceView.formatTimestamp(
                collectedAt, ZoneId.systemDefault()),
                InvestigatorWorkspaceView.formatCheckoutTimestamp(collectedAt));
    }

    @Test
    void formatsInvestigatorTimestampsInTheWorkstationZone() {
        assertEquals("26/09/2026 17:05", InvestigatorWorkspaceView.formatTimestamp(
                Instant.parse("2026-09-26T09:05:45Z"), ZoneId.of("Asia/Singapore")));
    }

    @Test
    void formatsCompactNoteRowsAndFullSelectedNoteDetails() {
        CheckoutViews.ExaminationNote note = new CheckoutViews.ExaminationNote(
                new ExaminationNoteId(UUID.randomUUID()), new CheckoutId(UUID.randomUUID()),
                new UserId(UUID.randomUUID()), "Alex Investigator",
                "First observation\nFurther detail", NOW, List.of(new CheckoutViews.NoteCorrection(
                        new UserId(UUID.randomUUID()), "Alex Investigator", "Updated finding",
                        "Clarified the observation", NOW.plusSeconds(60))));

        assertEquals("First observation…", InvestigatorWorkspaceView.notePreview(note.text()));
        String createdAt = InvestigatorWorkspaceView.formatTimestamp(
                NOW, ZoneId.systemDefault());
        String correctedAt = InvestigatorWorkspaceView.formatTimestamp(
                NOW.plusSeconds(60), ZoneId.systemDefault());
        assertEquals("Alex Investigator · " + createdAt,
                InvestigatorWorkspaceView.noteByline(note));
        assertEquals("1 correction", InvestigatorWorkspaceView.correctionCountLabel(note));
        assertEquals("""
                First observation
                Further detail
                ------------------------------------------------------
                Correction by Alex Investigator · %s
                Updated finding
                Reason: Clarified the observation
                ------------------------------------------------------""".formatted(correctedAt),
                InvestigatorWorkspaceView.formatSelectedNote(note));
    }

    @Test
    void formatsHistoryRowsWithSubjectsTransitionsAndCorrectionContent() {
        AuditEventId correctedEventId = new AuditEventId(UUID.randomUUID());
        HistoryViews.Event collectionAcknowledged = new HistoryViews.Event(
                new AuditEventId(UUID.randomUUID()), AuditEventType.COLLECTION_ACKNOWLEDGED,
                "Alex Investigator", Role.INVESTIGATOR, NOW,
                Optional.of(new EvidenceId(UUID.randomUUID())), Optional.of("EV-001"),
                Optional.of(new CheckoutRequestId(UUID.randomUUID())),
                Optional.of(new HandoffId(UUID.randomUUID())),
                Optional.of(new CheckoutId(UUID.randomUUID())),
                Optional.of(CheckoutRequestStatus.APPROVED),
                Optional.of(CheckoutRequestStatus.CONSUMED),
                Optional.of(EvidenceCustodyState.IN_STORAGE),
                Optional.of(EvidenceCustodyState.CHECKED_OUT),
                Optional.of("Corrected detail"), Optional.of("Clarified record"),
                Optional.of(correctedEventId));
        String text = InvestigatorWorkspaceView.historyText(collectionAcknowledged);

        assertTrue(text.contains("evidence EV-001"));
        assertTrue(text.contains("request "));
        assertTrue(text.contains("checkout "));
        assertTrue(text.contains("custody IN_STORAGE → CHECKED_OUT"));
        assertTrue(text.contains("correction: Corrected detail"));
        assertTrue(text.contains("reason: Clarified record"));
        assertTrue(text.contains("corrects event "));
    }

    @Test
    void usesTheRequestedColourForEachEvidenceCustodyState() {
        assertEquals(Color.GREEN, InvestigatorWorkspaceView.custodyStateColor(
                EvidenceCustodyState.IN_STORAGE));
        assertEquals(Color.ORANGE, InvestigatorWorkspaceView.custodyStateColor(
                EvidenceCustodyState.HANDOFF_AWAITING_ACK));
        assertEquals(Color.ORANGE, InvestigatorWorkspaceView.custodyStateColor(
                EvidenceCustodyState.HANDIN_AWAITING_ACK));
        assertEquals(Color.RED, InvestigatorWorkspaceView.custodyStateColor(
                EvidenceCustodyState.CHECKED_OUT));
        assertEquals(Color.GRAY, InvestigatorWorkspaceView.custodyStateColor(
                EvidenceCustodyState.VOIDED));
    }

    @Test
    void usesTheRequestedColourForEachRequestStatus() {
        assertEquals(Color.ORANGE, InvestigatorWorkspaceView.requestStatusColor(
                CheckoutRequestStatus.PENDING));
        assertEquals(Color.GREEN, InvestigatorWorkspaceView.requestStatusColor(
                CheckoutRequestStatus.APPROVED));
        assertEquals(Color.GREEN, InvestigatorWorkspaceView.requestStatusColor(
                CheckoutRequestStatus.CONSUMED));
        assertEquals(Color.RED, InvestigatorWorkspaceView.requestStatusColor(
                CheckoutRequestStatus.REJECTED));
        assertEquals(Color.RED, InvestigatorWorkspaceView.requestStatusColor(
                CheckoutRequestStatus.WITHDRAWN));
        assertEquals(Color.RED, InvestigatorWorkspaceView.requestStatusColor(
                CheckoutRequestStatus.CANCELLED));
    }

    @Test
    void enablesInitialActionsOnlyForPermittedSelectedStates() {
        InvestigatorWorkspaceView.ActionAvailability availability =
                InvestigatorWorkspaceView.actionAvailability(
                        evidence(EvidenceCustodyState.IN_STORAGE),
                        request(CheckoutRequestStatus.PENDING, Optional.empty()),
                        checkout(EvidenceCustodyState.CHECKED_OUT, Optional.empty()),
                        note());

        assertTrue(availability.canSubmitRequest());
        assertTrue(availability.canWithdrawRequest());
        assertFalse(availability.canAcknowledgeCollection());
        assertTrue(availability.canEditCheckout());
        assertTrue(availability.canCorrectNote());
    }

    @Test
    void enablesAcknowledgementOnlyForApprovedRequestWithHandoff() {
        InvestigatorWorkspaceView.ActionAvailability availability =
                InvestigatorWorkspaceView.actionAvailability(
                        evidence(EvidenceCustodyState.HANDOFF_AWAITING_ACK),
                        request(CheckoutRequestStatus.APPROVED,
                                Optional.of(new HandoffId(UUID.randomUUID()))),
                        null,
                        null);

        assertFalse(availability.canSubmitRequest());
        assertFalse(availability.canWithdrawRequest());
        assertTrue(availability.canAcknowledgeCollection());
        assertFalse(availability.canEditCheckout());
        assertFalse(availability.canCorrectNote());
    }

    @Test
    void freezesCheckoutEditingAfterReturnButKeepsNoteCorrectionAvailable() {
        InvestigatorWorkspaceView.ActionAvailability availability =
                InvestigatorWorkspaceView.actionAvailability(
                        evidence(EvidenceCustodyState.HANDIN_AWAITING_ACK),
                        null,
                        checkout(EvidenceCustodyState.HANDIN_AWAITING_ACK,
                                Optional.of(NOW)),
                        note());

        assertFalse(availability.canEditCheckout());
        assertTrue(availability.canCorrectNote());
    }

    @Test
    void keepsControlsBusyUntilUiCompletionThenRefreshesOnSuccess() {
        List<Runnable> queuedTasks = new ArrayList<>();
        AtomicBoolean busy = new AtomicBoolean();
        AtomicReference<Runnable> uiTask = new AtomicReference<>();
        AtomicReference<String> refreshed = new AtomicReference<>();

        InvestigatorWorkspaceView.dispatchTask(queuedTasks::add, () ->
                new InvestigatorController.Result<>(true, "loaded", ""),
                busy::set,
                uiTask::set,
                refreshed::set,
                ignored -> { });

        assertTrue(busy.get());
        assertTrue(uiTask.get() == null);
        queuedTasks.removeFirst().run();
        assertTrue(busy.get());
        uiTask.get().run();
        assertFalse(busy.get());
        assertTrue("loaded".equals(refreshed.get()));
    }

    @Test
    void rendersSafeFailureAfterTheBackgroundTaskReturns() {
        AtomicBoolean busy = new AtomicBoolean();
        AtomicReference<Runnable> uiTask = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        Executor executor = Runnable::run;
        Supplier<InvestigatorController.Result<String>> task = () ->
                new InvestigatorController.Result<>(false, null, "Request could not be changed");

        InvestigatorWorkspaceView.dispatchTask(
                executor, task, busy::set, uiTask::set, ignored -> { }, error::set);

        assertTrue(busy.get());
        uiTask.get().run();
        assertFalse(busy.get());
        assertTrue("Request could not be changed".equals(error.get()));
    }

    @Test
    void unexpectedBackgroundFailureRestoresBusyStateAndShowsSafeMessage() {
        AtomicBoolean busy = new AtomicBoolean();
        AtomicReference<Runnable> uiTask = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        InvestigatorWorkspaceView.dispatchTask(Runnable::run, () -> {
            throw new IllegalStateException("database path detail");
        }, busy::set, uiTask::set, ignored -> { }, error::set);

        assertTrue(busy.get());
        uiTask.get().run();
        assertFalse(busy.get());
        assertEquals("The operation could not be completed. Please try again.", error.get());
    }

    private static CaseworkViews.Evidence evidence(EvidenceCustodyState state) {
        return new CaseworkViews.Evidence(
                new EvidenceId(UUID.randomUUID()),
                new CaseId(UUID.randomUUID()),
                "Case One", "EV-001", "Sealed bag",
                new StorageLocationId(UUID.randomUUID()), "Locker A", state, NOW);
    }

    private static CheckoutViews.Request request(
            CheckoutRequestStatus status, Optional<HandoffId> handoffId) {
        return new CheckoutViews.Request(
                new CheckoutRequestId(UUID.randomUUID()),
                new EvidenceId(UUID.randomUUID()), "EV-001", "Sealed bag", "Locker A",
                new CaseId(UUID.randomUUID()),
                "Case One", new UserId(UUID.randomUUID()), "Alex Investigator",
                "Review seal", NOW.plusSeconds(3600), status,
                EvidenceCustodyState.IN_STORAGE, NOW, handoffId, Optional.empty());
    }

    private static CheckoutViews.Checkout checkout(
            EvidenceCustodyState state, Optional<Instant> returnInitiatedAt) {
        return new CheckoutViews.Checkout(
                new CheckoutId(UUID.randomUUID()), new CheckoutRequestId(UUID.randomUUID()),
                new EvidenceId(UUID.randomUUID()), "EV-001", new CaseId(UUID.randomUUID()),
                "Case One", new UserId(UUID.randomUUID()), "Alex Investigator", NOW,
                returnInitiatedAt, Optional.empty(), state);
    }

    private static CheckoutViews.ExaminationNote note() {
        return new CheckoutViews.ExaminationNote(
                new ExaminationNoteId(UUID.randomUUID()), new CheckoutId(UUID.randomUUID()),
                new UserId(UUID.randomUUID()), "Alex Investigator", "Observed seal", NOW,
                java.util.List.of());
    }

    private static HistoryViews.Event history(AuditEventType type) {
        return new HistoryViews.Event(
                new AuditEventId(UUID.randomUUID()), type, "Alex Investigator", Role.INVESTIGATOR,
                NOW,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }
}
