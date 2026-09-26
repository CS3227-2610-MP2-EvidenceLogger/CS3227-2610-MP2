package evidencelogger.ui.custodian;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import evidencelogger.domain.UserId;
import evidencelogger.service.ServiceException;
import evidencelogger.service.checkout.CheckoutCommandService;
import evidencelogger.service.checkout.CheckoutQueryService;
import evidencelogger.service.dto.CheckoutCommands;
import evidencelogger.service.dto.CheckoutViews;
import evidencelogger.service.dto.HistoryCommands;
import evidencelogger.service.dto.HistoryViews;
import evidencelogger.service.history.HistoryCommandService;
import evidencelogger.service.history.HistoryQueryService;

class CustodianWorkflowControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-26T06:00:00Z");
    private static final CaseId CASE_ID = new CaseId(UUID.randomUUID());

    private RecordingCommands commands;
    private RecordingQueries queries;
    private RecordingHistory history;
    private RecordingHistoryCommands historyCommands;
    private CustodianWorkflowController controller;

    @BeforeEach
    void setUp() {
        commands = new RecordingCommands();
        queries = new RecordingQueries();
        history = new RecordingHistory();
        historyCommands = new RecordingHistoryCommands();
        controller = new CustodianWorkflowController(
                commands, queries, historyCommands, history);
    }

    @Test
    void sendsRequestDecisionAndHandoffCommandsForSelectedRequest() {
        CheckoutViews.Request request = request(
                CheckoutRequestStatus.APPROVED,
                EvidenceCustodyState.HANDOFF_AWAITING_ACK,
                Optional.of(new HandoffId(UUID.randomUUID())));

        assertTrue(controller.approve(request).successful());
        assertTrue(controller.reject(request).successful());
        assertTrue(controller.cancel(request, "No longer needed").successful());
        assertTrue(controller.recordHandoff(request).successful());
        assertTrue(controller.reverseHandoff(request, "Collection cancelled").successful());

        assertEquals(request.requestId(), commands.approvedRequestId);
        assertEquals(request.requestId(), commands.rejectedRequestId);
        assertEquals(request.requestId(), commands.cancelledRequestId);
        assertEquals("No longer needed", commands.cancellationReason);
        assertEquals(request.requestId(), commands.handoffRequestId);
        assertEquals(request.handoffId().orElseThrow(), commands.reversedHandoffId);
        assertEquals("Collection cancelled", commands.reversalReason);
    }

    @Test
    void validatesSelectionsAndReasonsBeforeCallingCommands() {
        CheckoutViews.Request request = request(
                CheckoutRequestStatus.APPROVED,
                EvidenceCustodyState.IN_STORAGE,
                Optional.empty());

        CustodianWorkflowController.Result<Void> missing = controller.approve(null);
        CustodianWorkflowController.Result<Void> blankCancel = controller.cancel(request, " ");
        CustodianWorkflowController.Result<Void> noHandoff =
                controller.reverseHandoff(request, "Mistake");

        assertFalse(missing.successful());
        assertEquals("Select a pending request", missing.message());
        assertFalse(blankCancel.successful());
        assertEquals("Cancellation reason is required", blankCancel.message());
        assertFalse(noHandoff.successful());
        assertEquals("Select an unacknowledged handoff", noHandoff.message());
        assertNull(commands.approvedRequestId);
        assertNull(commands.cancelledRequestId);
        assertNull(commands.reversedHandoffId);
    }

    @Test
    void sendsExpectedAndUnplannedInspectionCommandsWithStoredOutcome() {
        CheckoutViews.Checkout checkout = checkout(EvidenceCustodyState.HANDIN_AWAITING_ACK);

        assertTrue(controller.inspectReturn(checkout).successful());
        assertTrue(controller.inspectUnplannedReturn(checkout, "Returned at desk").successful());

        assertEquals(checkout.checkoutId(), commands.inspectedCheckoutId);
        assertEquals(evidencelogger.domain.ReturnInspectionOutcome.STORED,
                commands.inspectionOutcome);
        assertEquals(checkout.checkoutId(), commands.unplannedCheckoutId);
        assertEquals("Returned at desk", commands.unplannedReason);
    }

    @Test
    void forwardsSharedQueriesWithoutUiFiltering() {
        CheckoutViews.Request request = request(
                CheckoutRequestStatus.PENDING,
                EvidenceCustodyState.IN_STORAGE,
                Optional.empty());
        CheckoutViews.Checkout checkout = checkout(EvidenceCustodyState.CHECKED_OUT);
        queries.requests = List.of(request);
        queries.checkouts = List.of(checkout);
        history.events = List.of();

        assertEquals(List.of(request), controller.listRequests().value());
        assertEquals(List.of(checkout), controller.listCheckouts().value());
        assertEquals(List.of(), controller.listHistory(CASE_ID).value());
        assertEquals(CASE_ID, history.caseId);
    }

    @Test
    void mapsTypedServiceFailureToReadableMessage() {
        commands.failure = new ServiceException.InvalidTransition(
                "The request is no longer pending");

        CustodianWorkflowController.Result<Void> result = controller.approve(request(
                CheckoutRequestStatus.PENDING,
                EvidenceCustodyState.IN_STORAGE,
                Optional.empty()));

        assertFalse(result.successful());
        assertEquals("The request is no longer pending", result.message());
    }

    @Test
    void validatesAndForwardsDocumentaryHistoryCorrection() {
        AuditEventId eventId = new AuditEventId(UUID.randomUUID());
        HistoryViews.Event event = new HistoryViews.Event(
                eventId,
                AuditEventType.EVIDENCE_REGISTERED,
                "Morgan Custodian",
                evidencelogger.domain.Role.EVIDENCE_CUSTODIAN,
                NOW,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        assertFalse(controller.correctHistory(null, "Corrected", "Mistake").successful());
        assertFalse(controller.correctHistory(event, " ", "Mistake").successful());
        assertFalse(controller.correctHistory(event, "Corrected", " ").successful());
        assertTrue(controller.correctHistory(event, "Corrected", "Mistake").successful());
        assertEquals(eventId, historyCommands.command.eventId());
        assertEquals("Corrected", historyCommands.command.correctionText());
        assertEquals("Mistake", historyCommands.command.reason());
    }

    private static CheckoutViews.Request request(
            CheckoutRequestStatus status,
            EvidenceCustodyState state,
            Optional<HandoffId> handoffId) {
        return new CheckoutViews.Request(
                new CheckoutRequestId(UUID.randomUUID()),
                new EvidenceId(UUID.randomUUID()),
                "EV-001",
                CASE_ID,
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

    private static CheckoutViews.Checkout checkout(EvidenceCustodyState state) {
        return new CheckoutViews.Checkout(
                new CheckoutId(UUID.randomUUID()),
                new CheckoutRequestId(UUID.randomUUID()),
                new EvidenceId(UUID.randomUUID()),
                "EV-001",
                CASE_ID,
                "Case One",
                new UserId(UUID.randomUUID()),
                "Alex Investigator",
                NOW,
                Optional.empty(),
                Optional.empty(),
                state);
    }

    private static final class RecordingCommands implements CheckoutCommandService {
        private CheckoutRequestId approvedRequestId;
        private CheckoutRequestId rejectedRequestId;
        private CheckoutRequestId cancelledRequestId;
        private String cancellationReason;
        private CheckoutRequestId handoffRequestId;
        private HandoffId reversedHandoffId;
        private String reversalReason;
        private CheckoutId inspectedCheckoutId;
        private CheckoutId unplannedCheckoutId;
        private evidencelogger.domain.ReturnInspectionOutcome inspectionOutcome;
        private String unplannedReason;
        private ServiceException failure;

        private void failIfConfigured() {
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public CheckoutRequestId submitRequest(CheckoutCommands.SubmitRequest command) {
            return null;
        }

        @Override
        public void withdrawRequest(CheckoutCommands.WithdrawRequest command) {
        }

        @Override
        public void approveRequest(CheckoutCommands.ApproveRequest command) {
            failIfConfigured();
            approvedRequestId = command.requestId();
        }

        @Override
        public void rejectRequest(CheckoutCommands.RejectRequest command) {
            rejectedRequestId = command.requestId();
        }

        @Override
        public void cancelApprovedRequest(CheckoutCommands.CancelApprovedRequest command) {
            cancelledRequestId = command.requestId();
            cancellationReason = command.reason();
        }

        @Override
        public HandoffId recordHandoff(CheckoutCommands.RecordHandoff command) {
            handoffRequestId = command.requestId();
            return new HandoffId(UUID.randomUUID());
        }

        @Override
        public void reverseHandoff(CheckoutCommands.ReverseHandoff command) {
            reversedHandoffId = command.handoffId();
            reversalReason = command.reason();
        }

        @Override
        public CheckoutId acknowledgeCollection(CheckoutCommands.AcknowledgeCollection command) {
            return null;
        }

        @Override
        public ExaminationNoteId addExaminationNote(CheckoutCommands.AddExaminationNote command) {
            return null;
        }

        @Override
        public void correctExaminationNote(CheckoutCommands.CorrectExaminationNote command) {
        }

        @Override
        public void initiateReturn(CheckoutCommands.InitiateReturn command) {
        }

        @Override
        public void inspectReturn(CheckoutCommands.InspectReturn command) {
            inspectedCheckoutId = command.checkoutId();
            inspectionOutcome = command.outcome();
        }

        @Override
        public void inspectUnplannedReturn(CheckoutCommands.InspectUnplannedReturn command) {
            unplannedCheckoutId = command.checkoutId();
            inspectionOutcome = command.outcome();
            unplannedReason = command.reason();
        }
    }

    private static final class RecordingQueries implements CheckoutQueryService {
        private List<CheckoutViews.Request> requests = List.of();
        private List<CheckoutViews.Checkout> checkouts = List.of();

        @Override
        public List<CheckoutViews.Request> listRequests() {
            return requests;
        }

        @Override
        public List<CheckoutViews.Request> listRequests(CheckoutRequestStatus status) {
            return requests;
        }

        @Override
        public List<CheckoutViews.Request> listRequestsForCase(CaseId caseId) {
            return requests;
        }

        @Override
        public CheckoutViews.Request getRequest(CheckoutRequestId requestId) {
            return requests.getFirst();
        }

        @Override
        public List<CheckoutViews.Checkout> listCheckouts() {
            return checkouts;
        }

        @Override
        public List<CheckoutViews.Checkout> listCheckoutsForCase(CaseId caseId) {
            return checkouts;
        }

        @Override
        public CheckoutViews.Checkout getCheckout(CheckoutId checkoutId) {
            return checkouts.getFirst();
        }

        @Override
        public List<CheckoutViews.ExaminationNote> listNotes(CheckoutId checkoutId) {
            return List.of();
        }
    }

    private static final class RecordingHistory implements HistoryQueryService {
        private CaseId caseId;
        private List<HistoryViews.Event> events = List.of();

        @Override
        public List<HistoryViews.Event> listEventsForCase(CaseId selectedCaseId) {
            caseId = selectedCaseId;
            return events;
        }
    }

    private static final class RecordingHistoryCommands implements HistoryCommandService {
        private HistoryCommands.CorrectEvent command;

        @Override
        public void correctEvent(HistoryCommands.CorrectEvent correction) {
            command = correction;
        }
    }
}
