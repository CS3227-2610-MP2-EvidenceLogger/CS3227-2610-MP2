package evidencelogger.ui.custodian;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.UserId;
import evidencelogger.service.dto.CheckoutViews;

class CustodianWorkflowViewTest {
    private static final Instant NOW = Instant.parse("2026-09-26T06:00:00Z");

    @Test
    void enablesDecisionOnlyForPendingRequest() {
        CustodianWorkflowView.ActionAvailability availability =
                CustodianWorkflowView.actionAvailability(
                        request(CheckoutRequestStatus.PENDING,
                                EvidenceCustodyState.IN_STORAGE, Optional.empty()),
                        null);

        assertTrue(availability.canDecide());
        assertFalse(availability.canCancel());
        assertFalse(availability.canRecordHandoff());
        assertFalse(availability.canReverseHandoff());
    }

    @Test
    void separatesApprovedHandoffAndReversalActions() {
        CustodianWorkflowView.ActionAvailability approved =
                CustodianWorkflowView.actionAvailability(
                        request(CheckoutRequestStatus.APPROVED,
                                EvidenceCustodyState.IN_STORAGE, Optional.empty()),
                        null);
        CustodianWorkflowView.ActionAvailability handedOff =
                CustodianWorkflowView.actionAvailability(
                        request(CheckoutRequestStatus.APPROVED,
                                EvidenceCustodyState.HANDOFF_AWAITING_ACK,
                                Optional.of(new HandoffId(UUID.randomUUID()))),
                        null);

        assertTrue(approved.canCancel());
        assertTrue(approved.canRecordHandoff());
        assertFalse(approved.canReverseHandoff());
        assertFalse(handedOff.canCancel());
        assertFalse(handedOff.canRecordHandoff());
        assertTrue(handedOff.canReverseHandoff());
    }

    @Test
    void separatesInitiatedAndUnplannedReturnInspectionActions() {
        CustodianWorkflowView.ActionAvailability initiated =
                CustodianWorkflowView.actionAvailability(null,
                        checkout(EvidenceCustodyState.HANDIN_AWAITING_ACK, Optional.of(NOW)));
        CustodianWorkflowView.ActionAvailability unplanned =
                CustodianWorkflowView.actionAvailability(null,
                        checkout(EvidenceCustodyState.CHECKED_OUT, Optional.empty()));

        assertTrue(initiated.canInspectReturn());
        assertFalse(initiated.canInspectUnplannedReturn());
        assertFalse(unplanned.canInspectReturn());
        assertTrue(unplanned.canInspectUnplannedReturn());
    }

    @Test
    void completesBackgroundResultOnUiDispatcher() {
        List<Runnable> queuedTasks = new ArrayList<>();
        AtomicBoolean busy = new AtomicBoolean();
        AtomicReference<Runnable> uiTask = new AtomicReference<>();
        AtomicReference<String> value = new AtomicReference<>();

        CustodianWorkflowView.dispatchTask(queuedTasks::add, () ->
                new CustodianWorkflowController.Result<>(true, "loaded", ""),
                busy::set,
                uiTask::set,
                value::set,
                ignored -> { });

        assertTrue(busy.get());
        queuedTasks.removeFirst().run();
        assertTrue(busy.get());
        uiTask.get().run();
        assertFalse(busy.get());
        assertTrue("loaded".equals(value.get()));
    }

    @Test
    void requestDecisionTextIncludesPurposeAndExpectedReturn() {
        String text = CustodianWorkflowView.requestText(request(
                CheckoutRequestStatus.PENDING,
                EvidenceCustodyState.IN_STORAGE,
                Optional.empty()));

        assertTrue(text.contains("purpose: Review seal"));
        assertTrue(text.contains("due "));
    }

    @Test
    void unexpectedBackgroundFailureRestoresBusyStateAndShowsSafeMessage() {
        AtomicBoolean busy = new AtomicBoolean();
        AtomicReference<Runnable> uiTask = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        CustodianWorkflowView.dispatchTask(Runnable::run, () -> {
            throw new IllegalStateException("database path detail");
        },
                busy::set,
                uiTask::set,
                ignored -> { },
                error::set);

        assertTrue(busy.get());
        uiTask.get().run();
        assertFalse(busy.get());
        assertEquals("The operation could not be completed. Please try again.", error.get());
    }

    private static CheckoutViews.Request request(
            CheckoutRequestStatus status,
            EvidenceCustodyState state,
            Optional<HandoffId> handoffId) {
        return new CheckoutViews.Request(
                new CheckoutRequestId(UUID.randomUUID()),
                new EvidenceId(UUID.randomUUID()),
                "EV-001",
                "Sealed bag",
                "Locker A",
                new CaseId(UUID.randomUUID()),
                "Case One",
                new UserId(UUID.randomUUID()),
                "Alex Investigator",
                "Review seal",
                NOW.plusSeconds(3600),
                status,
                state,
                NOW,
                handoffId,
                Optional.empty());
    }

    private static CheckoutViews.Checkout checkout(
            EvidenceCustodyState state,
            Optional<Instant> returnInitiatedAt) {
        return new CheckoutViews.Checkout(
                new CheckoutId(UUID.randomUUID()),
                new CheckoutRequestId(UUID.randomUUID()),
                new EvidenceId(UUID.randomUUID()),
                "EV-001",
                new CaseId(UUID.randomUUID()),
                "Case One",
                new UserId(UUID.randomUUID()),
                "Alex Investigator",
                NOW,
                returnInitiatedAt,
                Optional.empty(),
                state);
    }
}
