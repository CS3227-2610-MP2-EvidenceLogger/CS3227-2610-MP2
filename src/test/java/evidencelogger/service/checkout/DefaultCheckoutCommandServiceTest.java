package evidencelogger.service.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.ReturnInspectionOutcome;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.repository.EvidenceRecord;
import evidencelogger.repository.EvidenceRepository;
import evidencelogger.repository.checkout.CheckoutRequestRecord;
import evidencelogger.repository.checkout.CheckoutRequestRepository;
import evidencelogger.repository.checkout.CheckoutRecord;
import evidencelogger.repository.checkout.CheckoutRepository;
import evidencelogger.repository.checkout.HandoffRecord;
import evidencelogger.repository.checkout.HandoffRepository;
import evidencelogger.repository.checkout.ExaminationNoteRecord;
import evidencelogger.repository.checkout.ExaminationNoteRepository;
import evidencelogger.repository.checkout.NoteCorrectionRecord;
import evidencelogger.repository.checkout.ReturnInspectionRecord;
import evidencelogger.repository.checkout.ReturnInspectionRepository;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.AuthorizationService;
import evidencelogger.service.dto.CheckoutCommands;
import evidencelogger.service.history.AuditEventDraft;
import evidencelogger.service.history.AuditEventWriter;

class DefaultCheckoutCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-24T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UserId INVESTIGATOR_ID = new UserId(UUID.randomUUID());
    private static final UserId OTHER_INVESTIGATOR_ID = new UserId(UUID.randomUUID());
    private static final CaseId CASE_ID = new CaseId(UUID.randomUUID());
    private static final EvidenceId EVIDENCE_ID = new EvidenceId(UUID.randomUUID());
    private static final CheckoutRequestId REQUEST_ID = new CheckoutRequestId(UUID.randomUUID());
    private static final HandoffId HANDOFF_ID = new HandoffId(UUID.randomUUID());
    private static final CheckoutId CHECKOUT_ID = new CheckoutId(UUID.randomUUID());
    private static final ExaminationNoteId NOTE_ID = new ExaminationNoteId(UUID.randomUUID());

    private FakeAuthorization authorization;
    private FakeEvidenceRepository evidence;
    private FakeRequestRepository requests;
    private FakeHandoffRepository handoffs;
    private FakeCheckoutRepository checkouts;
    private FakeNoteRepository notes;
    private FakeInspectionRepository inspections;
    private FakeAuditWriter audit;
    private DefaultCheckoutCommandService service;

    @BeforeEach
    void setUp() {
        authorization = new FakeAuthorization(INVESTIGATOR_ID);
        evidence = new FakeEvidenceRepository();
        evidence.records.put(EVIDENCE_ID, new EvidenceRecord(
                EVIDENCE_ID, CASE_ID, EvidenceCustodyState.IN_STORAGE));
        requests = new FakeRequestRepository();
        handoffs = new FakeHandoffRepository(requests);
        checkouts = new FakeCheckoutRepository(requests, handoffs);
        notes = new FakeNoteRepository();
        inspections = new FakeInspectionRepository();
        audit = new FakeAuditWriter();
        service = new DefaultCheckoutCommandService(
                new TransactionRunner() {
                    @Override
                    public <T> T inTransaction(TransactionalWork<T> work) {
                        return work.execute(null);
                    }
                },
                authorization,
                evidence,
                requests,
                handoffs,
                checkouts,
                notes,
                inspections,
                audit,
                () -> REQUEST_ID,
                () -> HANDOFF_ID,
                () -> CHECKOUT_ID,
                () -> NOTE_ID,
                CLOCK);
    }

    @Test
    void assignedInvestigatorCanSubmitRequestAndAuditIsAppended() {
        CheckoutRequestId result = service.submitRequest(new CheckoutCommands.SubmitRequest(
                EVIDENCE_ID, "Review item", NOW.plusSeconds(3600)));

        assertEquals(REQUEST_ID, result);
        CheckoutRequestRecord request = requests.byId.get(REQUEST_ID);
        assertEquals(CheckoutRequestStatus.PENDING, request.status());
        assertEquals(INVESTIGATOR_ID, request.requesterId());
        assertEquals(AuditEventType.REQUEST_SUBMITTED, audit.events.get(0).type());
        assertEquals(Optional.of(CheckoutRequestStatus.PENDING),
                audit.events.get(0).resultingRequestStatus());
    }

    @Test
    void submissionRejectsReturnTimeThatIsNotLaterThanSubmission() {
        ServiceException.ValidationFailure failure = assertThrows(
                ServiceException.ValidationFailure.class,
                () -> service.submitRequest(new CheckoutCommands.SubmitRequest(
                        EVIDENCE_ID, "Review item", NOW)));

        assertTrue(failure.getMessage().contains("later"));
        assertTrue(requests.byId.isEmpty());
        assertTrue(audit.events.isEmpty());
    }

    @Test
    void submissionRejectsUnassignedInvestigator() {
        authorization.assigned = false;

        assertThrows(ServiceException.Forbidden.class, () -> service.submitRequest(
                new CheckoutCommands.SubmitRequest(
                        EVIDENCE_ID, "Review item", NOW.plusSeconds(3600))));
        assertTrue(requests.byId.isEmpty());
        assertTrue(audit.events.isEmpty());
    }

    @Test
    void submissionRejectsEvidenceOutsideStorageAndActiveRequest() {
        evidence.records.put(EVIDENCE_ID, new EvidenceRecord(
                EVIDENCE_ID, CASE_ID, EvidenceCustodyState.CHECKED_OUT));
        assertThrows(ServiceException.InvalidTransition.class, () -> service.submitRequest(
                new CheckoutCommands.SubmitRequest(
                        EVIDENCE_ID, "Review item", NOW.plusSeconds(3600))));

        evidence.records.put(EVIDENCE_ID, new EvidenceRecord(
                EVIDENCE_ID, CASE_ID, EvidenceCustodyState.IN_STORAGE));
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.APPROVED));
        assertThrows(ServiceException.Conflict.class, () -> service.submitRequest(
                new CheckoutCommands.SubmitRequest(
                        EVIDENCE_ID, "Review item", NOW.plusSeconds(3600))));
    }

    @Test
    void terminalRequestDoesNotBlockFreshSubmission() {
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.REJECTED));

        assertEquals(REQUEST_ID, service.submitRequest(new CheckoutCommands.SubmitRequest(
                EVIDENCE_ID, "Review item", NOW.plusSeconds(3600))));
        assertEquals(CheckoutRequestStatus.PENDING, requests.byId.get(REQUEST_ID).status());
    }

    @Test
    void requestingAssignedInvestigatorCanWithdrawPendingRequest() {
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.PENDING));

        service.withdrawRequest(new CheckoutCommands.WithdrawRequest(REQUEST_ID));

        assertEquals(CheckoutRequestStatus.WITHDRAWN, requests.byId.get(REQUEST_ID).status());
        assertEquals(AuditEventType.REQUEST_WITHDRAWN, audit.events.get(0).type());
    }

    @Test
    void withdrawalRejectsWrongRequesterAndRemovedAssignment() {
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.PENDING));
        requests.byId.put(REQUEST_ID, new CheckoutRequestRecord(
                REQUEST_ID, EVIDENCE_ID, OTHER_INVESTIGATOR_ID, "Review item",
                NOW.plusSeconds(3600), CheckoutRequestStatus.PENDING, NOW));
        assertThrows(ServiceException.Forbidden.class, () -> service.withdrawRequest(
                new CheckoutCommands.WithdrawRequest(REQUEST_ID)));

        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.PENDING));
        authorization.assigned = false;
        assertThrows(ServiceException.Forbidden.class, () -> service.withdrawRequest(
                new CheckoutCommands.WithdrawRequest(REQUEST_ID)));
    }

    @Test
    void withdrawalRejectsTerminalRequestWithoutAddingAuditEvent() {
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.REJECTED));

        assertThrows(ServiceException.InvalidTransition.class, () -> service.withdrawRequest(
                new CheckoutCommands.WithdrawRequest(REQUEST_ID)));
        assertTrue(audit.events.isEmpty());
    }

    @Test
    void custodianCanApprovePendingRequestWhenRequesterRemainsAssigned() {
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.PENDING));
        authorization.custodian = true;

        service.approveRequest(new CheckoutCommands.ApproveRequest(REQUEST_ID));

        assertEquals(CheckoutRequestStatus.APPROVED, requests.byId.get(REQUEST_ID).status());
        assertEquals(AuditEventType.REQUEST_APPROVED, audit.events.get(0).type());
    }

    @Test
    void approvalRejectsRequestAfterRequesterLosesAssignment() {
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.PENDING));
        authorization.custodian = true;
        authorization.assigned = false;

        assertThrows(ServiceException.Forbidden.class, () -> service.approveRequest(
                new CheckoutCommands.ApproveRequest(REQUEST_ID)));
        assertEquals(CheckoutRequestStatus.PENDING, requests.byId.get(REQUEST_ID).status());
        assertTrue(audit.events.isEmpty());
    }

    @Test
    void custodianCanRejectPendingRequestWithoutChangingCustody() {
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.PENDING));
        evidence.records.put(EVIDENCE_ID, new EvidenceRecord(
                EVIDENCE_ID, CASE_ID, EvidenceCustodyState.CHECKED_OUT));
        authorization.custodian = true;

        service.rejectRequest(new CheckoutCommands.RejectRequest(REQUEST_ID));

        assertEquals(CheckoutRequestStatus.REJECTED, requests.byId.get(REQUEST_ID).status());
        assertEquals(AuditEventType.REQUEST_REJECTED, audit.events.get(0).type());
        assertEquals(EvidenceCustodyState.CHECKED_OUT,
                evidence.records.get(EVIDENCE_ID).custodyState());
    }

    @Test
    void custodianCanCancelApprovedRequestWithReason() {
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.APPROVED));
        authorization.custodian = true;

        service.cancelApprovedRequest(new CheckoutCommands.CancelApprovedRequest(
                REQUEST_ID, "No longer needed"));

        assertEquals(CheckoutRequestStatus.CANCELLED, requests.byId.get(REQUEST_ID).status());
        assertEquals(AuditEventType.REQUEST_CANCELLED, audit.events.get(0).type());
        assertEquals(Optional.of("No longer needed"), audit.events.get(0).reason());
    }

    @Test
    void custodianCanRecordHandoffForApprovedAssignedRequest() {
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.APPROVED));
        authorization.custodian = true;

        HandoffId result = service.recordHandoff(new CheckoutCommands.RecordHandoff(REQUEST_ID));

        assertEquals(HANDOFF_ID, result);
        assertEquals(AuditEventType.HANDOFF_RECORDED, audit.events.get(0).type());
        assertEquals(EvidenceCustodyState.HANDOFF_AWAITING_ACK,
                audit.events.get(0).resultingCustodyState().orElseThrow());
    }

    @Test
    void handoffRequiresApprovedRequestAndCurrentRequesterAssignment() {
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.PENDING));
        authorization.custodian = true;
        assertThrows(ServiceException.InvalidTransition.class, () -> service.recordHandoff(
                new CheckoutCommands.RecordHandoff(REQUEST_ID)));

        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.APPROVED));
        authorization.assigned = false;
        assertThrows(ServiceException.Forbidden.class, () -> service.recordHandoff(
                new CheckoutCommands.RecordHandoff(REQUEST_ID)));
    }

    @Test
    void custodianCanReverseUnacknowledgedHandoffWithReason() {
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.APPROVED));
        evidence.records.put(EVIDENCE_ID, new EvidenceRecord(
                EVIDENCE_ID, CASE_ID, EvidenceCustodyState.HANDOFF_AWAITING_ACK));
        handoffs.byId.put(HANDOFF_ID, new HandoffRecord(
                HANDOFF_ID, REQUEST_ID, EVIDENCE_ID, new UserId(UUID.randomUUID()), NOW,
                Optional.empty(), Optional.empty(), Optional.empty()));
        authorization.custodian = true;

        service.reverseHandoff(new CheckoutCommands.ReverseHandoff(
                HANDOFF_ID, "Collector unavailable"));

        assertEquals(AuditEventType.HANDOFF_REVERSED, audit.events.get(0).type());
        assertEquals(Optional.of("Collector unavailable"), audit.events.get(0).reason());
        assertEquals(CheckoutRequestStatus.CANCELLED, requests.byId.get(REQUEST_ID).status());
    }

    @Test
    void requestingInvestigatorCanAcknowledgeMatchingHandoff() {
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.APPROVED));
        evidence.records.put(EVIDENCE_ID, new EvidenceRecord(
                EVIDENCE_ID, CASE_ID, EvidenceCustodyState.HANDOFF_AWAITING_ACK));
        handoffs.byId.put(HANDOFF_ID, new HandoffRecord(
                HANDOFF_ID, REQUEST_ID, EVIDENCE_ID, new UserId(UUID.randomUUID()), NOW,
                Optional.empty(), Optional.empty(), Optional.empty()));

        CheckoutId checkoutId = service.acknowledgeCollection(
                new CheckoutCommands.AcknowledgeCollection(HANDOFF_ID));

        assertEquals(CheckoutRequestStatus.CONSUMED, requests.byId.get(REQUEST_ID).status());
        assertEquals(AuditEventType.COLLECTION_ACKNOWLEDGED, audit.events.get(0).type());
        assertEquals(checkoutId, audit.events.get(0).checkoutId().orElseThrow());
    }

    @Test
    void collectionRejectsWrongRequesterAndMissingHandoff() {
        assertThrows(ServiceException.NotFound.class, () -> service.acknowledgeCollection(
                new CheckoutCommands.AcknowledgeCollection(HANDOFF_ID)));

        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.APPROVED));
        evidence.records.put(EVIDENCE_ID, new EvidenceRecord(
                EVIDENCE_ID, CASE_ID, EvidenceCustodyState.HANDOFF_AWAITING_ACK));
        handoffs.byId.put(HANDOFF_ID, new HandoffRecord(
                HANDOFF_ID, REQUEST_ID, EVIDENCE_ID, new UserId(UUID.randomUUID()), NOW,
                Optional.empty(), Optional.empty(), Optional.empty()));
        requests.byId.put(REQUEST_ID, new CheckoutRequestRecord(
                REQUEST_ID, EVIDENCE_ID, OTHER_INVESTIGATOR_ID, "Review item",
                NOW.plusSeconds(3600), CheckoutRequestStatus.APPROVED, NOW));

        assertThrows(ServiceException.Forbidden.class, () -> service.acknowledgeCollection(
                new CheckoutCommands.AcknowledgeCollection(HANDOFF_ID)));
    }

    @Test
    void collectingInvestigatorCanAddNoteBeforeReturn() {
        seedActiveCheckout();

        ExaminationNoteId noteId = service.addExaminationNote(
                new CheckoutCommands.AddExaminationNote(CHECKOUT_ID, "Observed seal"));

        assertEquals(NOTE_ID, noteId);
        assertEquals("Observed seal", notes.notes.get(NOTE_ID).text());
        assertEquals(AuditEventType.EXAMINATION_NOTE_ADDED, audit.events.get(0).type());
    }

    @Test
    void notesAreFrozenAfterReturnInitiation() {
        seedActiveCheckout();
        checkouts.byId.put(CHECKOUT_ID, new CheckoutRecord(
                CHECKOUT_ID, REQUEST_ID, EVIDENCE_ID, INVESTIGATOR_ID, NOW,
                Optional.of(NOW.plusSeconds(60)), Optional.empty(),
                EvidenceCustodyState.HANDIN_AWAITING_ACK));
        evidence.records.put(EVIDENCE_ID, new EvidenceRecord(
                EVIDENCE_ID, CASE_ID, EvidenceCustodyState.HANDIN_AWAITING_ACK));

        assertThrows(ServiceException.InvalidTransition.class, () -> service.addExaminationNote(
                new CheckoutCommands.AddExaminationNote(CHECKOUT_ID, "Too late")));
    }

    @Test
    void originalAssignedNoteAuthorCanAppendCorrection() {
        seedActiveCheckout();
        notes.notes.put(NOTE_ID, new ExaminationNoteRecord(
                NOTE_ID, CHECKOUT_ID, INVESTIGATOR_ID, "Original", NOW));

        service.correctExaminationNote(new CheckoutCommands.CorrectExaminationNote(
                NOTE_ID, "Corrected seal description", "Clarified observation"));

        assertEquals(1, notes.corrections.size());
        assertEquals("Original", notes.notes.get(NOTE_ID).text());
        assertEquals(AuditEventType.EXAMINATION_NOTE_CORRECTED, audit.events.get(0).type());
        assertEquals(Optional.of(NOTE_ID), audit.events.get(0).correctedNoteId());
    }

    @Test
    void collectingInvestigatorCanInitiateReturnOnce() {
        seedActiveCheckout();

        service.initiateReturn(new CheckoutCommands.InitiateReturn(CHECKOUT_ID));

        assertEquals(AuditEventType.RETURN_INITIATED, audit.events.get(0).type());
        assertTrue(checkouts.byId.get(CHECKOUT_ID).returnInitiatedAt().isPresent());
    }

    @Test
    void custodianCanInspectInitiatedReturnAndStoreEvidence() {
        seedActiveCheckout();
        checkouts.byId.put(CHECKOUT_ID, new CheckoutRecord(
                CHECKOUT_ID, REQUEST_ID, EVIDENCE_ID, INVESTIGATOR_ID, NOW,
                Optional.of(NOW.plusSeconds(60)), Optional.empty(),
                EvidenceCustodyState.HANDIN_AWAITING_ACK));
        evidence.records.put(EVIDENCE_ID, new EvidenceRecord(
                EVIDENCE_ID, CASE_ID, EvidenceCustodyState.HANDIN_AWAITING_ACK));
        authorization.custodian = true;

        service.inspectReturn(new CheckoutCommands.InspectReturn(
                CHECKOUT_ID, ReturnInspectionOutcome.STORED));

        assertEquals(AuditEventType.RETURN_INSPECTED_STORED, audit.events.get(0).type());
        assertTrue(checkouts.byId.get(CHECKOUT_ID).completedAt().isPresent());
    }

    @Test
    void custodianCanRecordUnplannedStoredReturnWithReason() {
        seedActiveCheckout();
        authorization.custodian = true;

        service.inspectUnplannedReturn(new CheckoutCommands.InspectUnplannedReturn(
                CHECKOUT_ID, ReturnInspectionOutcome.STORED, "Delivered unexpectedly"));

        assertEquals(AuditEventType.UNPLANNED_RETURN_INSPECTED, audit.events.get(0).type());
        assertEquals(Optional.of("Delivered unexpectedly"), audit.events.get(0).reason());
        assertTrue(checkouts.byId.get(CHECKOUT_ID).completedAt().isPresent());
    }

    private void seedActiveCheckout() {
        requests.byId.put(REQUEST_ID, request(CheckoutRequestStatus.CONSUMED));
        evidence.records.put(EVIDENCE_ID, new EvidenceRecord(
                EVIDENCE_ID, CASE_ID, EvidenceCustodyState.CHECKED_OUT));
        checkouts.byId.put(CHECKOUT_ID, new CheckoutRecord(
                CHECKOUT_ID, REQUEST_ID, EVIDENCE_ID, INVESTIGATOR_ID, NOW,
                Optional.empty(), Optional.empty(), EvidenceCustodyState.CHECKED_OUT));
    }

    private static CheckoutRequestRecord request(CheckoutRequestStatus status) {
        return new CheckoutRequestRecord(
                REQUEST_ID,
                EVIDENCE_ID,
                INVESTIGATOR_ID,
                "Review item",
                NOW.plusSeconds(3600),
                status,
                NOW);
    }

    private static final class FakeEvidenceRepository implements EvidenceRepository {
        private final Map<EvidenceId, EvidenceRecord> records = new HashMap<>();

        @Override
        public Optional<EvidenceRecord> findById(Connection connection, EvidenceId evidenceId) {
            return Optional.ofNullable(records.get(evidenceId));
        }
    }

    private static final class FakeRequestRepository implements CheckoutRequestRepository {
        private final Map<CheckoutRequestId, CheckoutRequestRecord> byId = new HashMap<>();

        @Override
        public Optional<CheckoutRequestRecord> findById(
                Connection connection, CheckoutRequestId requestId) {
            return Optional.ofNullable(byId.get(requestId));
        }

        @Override
        public Optional<CheckoutRequestRecord> findPendingOrApprovedForEvidence(
                Connection connection, EvidenceId evidenceId) {
            return byId.values().stream()
                    .filter(request -> request.evidenceId().equals(evidenceId))
                    .filter(request -> request.status() == CheckoutRequestStatus.PENDING
                            || request.status() == CheckoutRequestStatus.APPROVED)
                    .findFirst();
        }

        @Override
        public List<CheckoutRequestRecord> findForEvidence(
                Connection connection, EvidenceId evidenceId) {
            return byId.values().stream()
                    .filter(request -> request.evidenceId().equals(evidenceId))
                    .toList();
        }

        @Override
        public void insertPending(Connection connection, CheckoutRequestRecord request) {
            byId.put(request.requestId(), request);
        }

        @Override
        public boolean transitionStatus(Connection connection, CheckoutRequestId requestId,
                CheckoutRequestStatus expected, CheckoutRequestStatus resulting) {
            CheckoutRequestRecord current = byId.get(requestId);
            if (current == null || current.status() != expected) {
                return false;
            }
            byId.put(requestId, new CheckoutRequestRecord(
                    current.requestId(), current.evidenceId(), current.requesterId(),
                    current.purpose(), current.expectedReturnAt(), resulting, current.submittedAt()));
            return true;
        }
    }

    private static final class FakeAuditWriter implements AuditEventWriter {
        private final List<AuditEventDraft> events = new ArrayList<>();

        @Override
        public evidencelogger.domain.AuditEventId append(
                Connection connection, AuditEventDraft event) {
            events.add(event);
            return new evidencelogger.domain.AuditEventId(UUID.randomUUID());
        }
    }

    private static final class FakeHandoffRepository implements HandoffRepository {
        private final Map<HandoffId, HandoffRecord> byId = new HashMap<>();
        private final FakeRequestRepository requests;

        private FakeHandoffRepository(FakeRequestRepository requests) {
            this.requests = requests;
        }

        @Override
        public Optional<HandoffRecord> findById(Connection connection, HandoffId handoffId) {
            return Optional.ofNullable(byId.get(handoffId));
        }

        @Override
        public Optional<HandoffRecord> findUnacknowledgedForRequest(
                Connection connection, CheckoutRequestId requestId) {
            return byId.values().stream()
                    .filter(handoff -> handoff.requestId().equals(requestId))
                    .filter(handoff -> handoff.acknowledgedAt().isEmpty()
                            && handoff.reversedAt().isEmpty())
                    .findFirst();
        }

        @Override
        public void insert(Connection connection, HandoffRecord handoff) {
            byId.put(handoff.handoffId(), handoff);
        }

        @Override
        public boolean acknowledge(Connection connection, HandoffId handoffId, Instant acknowledgedAt) {
            HandoffRecord handoff = byId.get(handoffId);
            if (handoff == null || handoff.acknowledgedAt().isPresent()
                    || handoff.reversedAt().isPresent()) {
                return false;
            }
            byId.put(handoffId, new HandoffRecord(
                    handoff.handoffId(), handoff.requestId(), handoff.evidenceId(),
                    handoff.custodianId(), handoff.recordedAt(), Optional.of(acknowledgedAt),
                    handoff.reversedAt(), handoff.reversalReason()));
            return true;
        }

        @Override
        public boolean reverse(
                Connection connection, HandoffId handoffId, String reason, Instant reversedAt) {
            HandoffRecord handoff = byId.get(handoffId);
            if (handoff == null || handoff.acknowledgedAt().isPresent()
                    || handoff.reversedAt().isPresent()) {
                return false;
            }
            byId.put(handoffId, new HandoffRecord(
                    handoff.handoffId(), handoff.requestId(), handoff.evidenceId(),
                    handoff.custodianId(), handoff.recordedAt(), handoff.acknowledgedAt(),
                    Optional.of(reversedAt), Optional.of(reason)));
            requests.byId.put(handoff.requestId(), new CheckoutRequestRecord(
                    requests.byId.get(handoff.requestId()).requestId(),
                    requests.byId.get(handoff.requestId()).evidenceId(),
                    requests.byId.get(handoff.requestId()).requesterId(),
                    requests.byId.get(handoff.requestId()).purpose(),
                    requests.byId.get(handoff.requestId()).expectedReturnAt(),
                    CheckoutRequestStatus.CANCELLED,
                    requests.byId.get(handoff.requestId()).submittedAt()));
            return true;
        }
    }

    private static final class FakeCheckoutRepository implements CheckoutRepository {
        private final Map<CheckoutId, CheckoutRecord> byId = new HashMap<>();
        private final FakeRequestRepository requests;
        private final FakeHandoffRepository handoffs;

        private FakeCheckoutRepository(
                FakeRequestRepository requests, FakeHandoffRepository handoffs) {
            this.requests = requests;
            this.handoffs = handoffs;
        }

        @Override
        public Optional<CheckoutRecord> findById(Connection connection, CheckoutId checkoutId) {
            return Optional.ofNullable(byId.get(checkoutId));
        }

        @Override
        public Optional<CheckoutRecord> findActiveForEvidence(
                Connection connection, EvidenceId evidenceId) {
            return byId.values().stream()
                    .filter(checkout -> checkout.evidenceId().equals(evidenceId))
                    .filter(checkout -> checkout.completedAt().isEmpty())
                    .findFirst();
        }

        @Override
        public void insert(Connection connection, CheckoutRecord checkout) {
            byId.put(checkout.checkoutId(), checkout);
            CheckoutRequestRecord request = requests.byId.get(checkout.requestId());
            requests.byId.put(request.requestId(), new CheckoutRequestRecord(
                    request.requestId(), request.evidenceId(), request.requesterId(),
                    request.purpose(), request.expectedReturnAt(),
                    CheckoutRequestStatus.CONSUMED, request.submittedAt()));
        }

        @Override
        public boolean markReturnInitiated(
                Connection connection, CheckoutId checkoutId, Instant initiatedAt) {
            CheckoutRecord current = byId.get(checkoutId);
            if (current == null || current.returnInitiatedAt().isPresent()
                    || current.completedAt().isPresent()) {
                return false;
            }
            byId.put(checkoutId, new CheckoutRecord(
                    current.checkoutId(), current.requestId(), current.evidenceId(),
                    current.collectorId(), current.collectedAt(), Optional.of(initiatedAt),
                    current.completedAt(), EvidenceCustodyState.HANDIN_AWAITING_ACK));
            return true;
        }

        @Override
        public boolean complete(Connection connection, CheckoutId checkoutId, Instant completedAt) {
            CheckoutRecord current = byId.get(checkoutId);
            if (current == null || current.returnInitiatedAt().isEmpty()
                    || current.completedAt().isPresent()) {
                return false;
            }
            byId.put(checkoutId, new CheckoutRecord(
                    current.checkoutId(), current.requestId(), current.evidenceId(),
                    current.collectorId(), current.collectedAt(), current.returnInitiatedAt(),
                    Optional.of(completedAt), EvidenceCustodyState.IN_STORAGE));
            return true;
        }

        @Override
        public boolean completeUnplanned(
                Connection connection, CheckoutId checkoutId, Instant completedAt) {
            CheckoutRecord current = byId.get(checkoutId);
            if (current == null || current.returnInitiatedAt().isPresent()
                    || current.completedAt().isPresent()) {
                return false;
            }
            byId.put(checkoutId, new CheckoutRecord(
                    current.checkoutId(), current.requestId(), current.evidenceId(),
                    current.collectorId(), current.collectedAt(), current.returnInitiatedAt(),
                    Optional.of(completedAt), EvidenceCustodyState.IN_STORAGE));
            return true;
        }
    }

    private static final class FakeNoteRepository implements ExaminationNoteRepository {
        private final Map<ExaminationNoteId, ExaminationNoteRecord> notes = new HashMap<>();
        private final List<NoteCorrectionRecord> corrections = new ArrayList<>();

        @Override
        public Optional<ExaminationNoteRecord> findById(
                Connection connection, ExaminationNoteId noteId) {
            return Optional.ofNullable(notes.get(noteId));
        }

        @Override
        public List<ExaminationNoteRecord> findForCheckout(
                Connection connection, CheckoutId checkoutId) {
            return notes.values().stream()
                    .filter(note -> note.checkoutId().equals(checkoutId))
                    .toList();
        }

        @Override
        public List<NoteCorrectionRecord> findCorrections(
                Connection connection, ExaminationNoteId noteId) {
            return corrections.stream()
                    .filter(correction -> correction.noteId().equals(noteId))
                    .toList();
        }

        @Override
        public void insert(Connection connection, ExaminationNoteRecord note) {
            notes.put(note.noteId(), note);
        }

        @Override
        public void appendCorrection(Connection connection, NoteCorrectionRecord correction) {
            corrections.add(correction);
        }
    }

    private static final class FakeInspectionRepository implements ReturnInspectionRepository {
        private final Map<CheckoutId, ReturnInspectionRecord> inspections = new HashMap<>();

        @Override
        public Optional<ReturnInspectionRecord> findByCheckout(
                Connection connection, CheckoutId checkoutId) {
            return Optional.ofNullable(inspections.get(checkoutId));
        }

        @Override
        public void insert(Connection connection, ReturnInspectionRecord inspection) {
            inspections.put(inspection.checkoutId(), inspection);
        }
    }

    private static final class FakeAuthorization implements AuthorizationService {
        private final AuthenticatedSession session;
        private final AuthenticatedSession custodianSession = new AuthenticatedSession(
                new UserId(UUID.randomUUID()), Role.EVIDENCE_CUSTODIAN, "Custodian");
        private boolean assigned = true;
        private boolean custodian;

        private FakeAuthorization(UserId userId) {
            session = new AuthenticatedSession(userId, Role.INVESTIGATOR, "Investigator");
        }

        @Override
        public AuthenticatedSession requireCustodian() {
            if (!custodian) {
                throw new ServiceException.Forbidden("not a custodian");
            }
            return custodianSession;
        }

        @Override
        public AuthenticatedSession requireInvestigator() {
            return session;
        }

        @Override
        public AuthenticatedSession requireAssignedInvestigator(evidencelogger.domain.CaseId caseId) {
            if (!assigned) {
                throw new ServiceException.Forbidden("not assigned");
            }
            return session;
        }

        @Override
        public AuthenticatedSession requireAssignedInvestigator(
                Connection connection, CaseId caseId) {
            return requireAssignedInvestigator(caseId);
        }

        @Override
        public void requireAssignedInvestigator(
                Connection connection, CaseId caseId, UserId investigatorId) {
            if (!assigned) {
                throw new ServiceException.Forbidden("not assigned");
            }
        }

        @Override
        public AuthenticatedSession requireAssignedInvestigator(
                CaseId caseId, BiPredicate<CaseId, UserId> assignmentCheck) {
            if (!assignmentCheck.test(caseId, session.userId())) {
                throw new ServiceException.Forbidden("not assigned");
            }
            return session;
        }

        @Override
        public AuthenticatedSession requireCollectingInvestigator(
                evidencelogger.domain.CheckoutId checkoutId) {
            throw new ServiceException.Forbidden("not a collector");
        }

        @Override
        public AuthenticatedSession requireCollectingInvestigator(
                evidencelogger.domain.CheckoutId checkoutId,
                BiPredicate<evidencelogger.domain.CheckoutId, UserId> collectorCheck) {
            if (!collectorCheck.test(checkoutId, session.userId())) {
                throw new ServiceException.Forbidden("not a collector");
            }
            return session;
        }

        @Override
        public AuthenticatedSession requireCollectingInvestigator(
                Connection connection, evidencelogger.domain.CheckoutId checkoutId) {
            if (!assigned) {
                throw new ServiceException.Forbidden("not a collector");
            }
            return session;
        }
    }
}
