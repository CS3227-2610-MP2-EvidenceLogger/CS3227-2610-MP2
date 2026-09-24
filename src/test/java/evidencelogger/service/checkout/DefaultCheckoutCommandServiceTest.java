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
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.repository.AuthorizationRepository;
import evidencelogger.repository.EvidenceRecord;
import evidencelogger.repository.EvidenceRepository;
import evidencelogger.repository.checkout.CheckoutRequestRecord;
import evidencelogger.repository.checkout.CheckoutRequestRepository;
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

    private FakeAuthorization authorization;
    private FakeEvidenceRepository evidence;
    private FakeRequestRepository requests;
    private FakeAuditWriter audit;
    private DefaultCheckoutCommandService service;

    @BeforeEach
    void setUp() {
        authorization = new FakeAuthorization(INVESTIGATOR_ID);
        evidence = new FakeEvidenceRepository();
        evidence.records.put(EVIDENCE_ID, new EvidenceRecord(
                EVIDENCE_ID, CASE_ID, EvidenceCustodyState.IN_STORAGE));
        requests = new FakeRequestRepository();
        audit = new FakeAuditWriter();
        service = new DefaultCheckoutCommandService(
                new TransactionRunner() {
                    @Override
                    public <T> T inTransaction(TransactionalWork<T> work) {
                        return work.execute(null);
                    }
                },
                authorization,
                new FakeAuthorizationRepository(authorization),
                evidence,
                requests,
                audit,
                () -> REQUEST_ID,
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

    private static final class FakeAuthorization implements AuthorizationService {
        private final AuthenticatedSession session;
        private boolean assigned = true;

        private FakeAuthorization(UserId userId) {
            session = new AuthenticatedSession(userId, Role.INVESTIGATOR, "Investigator");
        }

        @Override
        public AuthenticatedSession requireCustodian() {
            throw new ServiceException.Forbidden("not a custodian");
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
            throw new ServiceException.Forbidden("not a collector");
        }
    }

    private static final class FakeAuthorizationRepository implements AuthorizationRepository {
        private final FakeAuthorization authorization;

        private FakeAuthorizationRepository(FakeAuthorization authorization) {
            this.authorization = authorization;
        }

        @Override
        public boolean isAssigned(CaseId caseId, UserId investigatorId) {
            return authorization.assigned;
        }

        @Override
        public boolean isAssigned(Connection connection, CaseId caseId, UserId investigatorId) {
            return authorization.assigned;
        }

        @Override
        public boolean isCollectingInvestigator(
                evidencelogger.domain.CheckoutId checkoutId, UserId investigatorId) {
            return false;
        }

        @Override
        public boolean isCollectingInvestigator(
                Connection connection,
                evidencelogger.domain.CheckoutId checkoutId,
                UserId investigatorId) {
            return false;
        }
    }
}
