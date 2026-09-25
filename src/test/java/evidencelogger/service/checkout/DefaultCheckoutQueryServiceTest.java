package evidencelogger.service.checkout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.repository.AuthorizationRepository;
import evidencelogger.repository.checkout.CheckoutReadRepository;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.DefaultAuthorizationService;
import evidencelogger.service.auth.SessionProvider;
import evidencelogger.service.dto.CheckoutViews;

class DefaultCheckoutQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-25T01:00:00Z");
    private static final UserId CUSTODIAN_ID = new UserId(UUID.randomUUID());
    private static final UserId ASSIGNED_INVESTIGATOR_ID = new UserId(UUID.randomUUID());
    private static final CaseId ASSIGNED_CASE_ID = new CaseId(UUID.randomUUID());
    private static final CaseId UNASSIGNED_CASE_ID = new CaseId(UUID.randomUUID());
    private static final CheckoutRequestId ASSIGNED_REQUEST_ID = new CheckoutRequestId(UUID.randomUUID());
    private static final CheckoutRequestId UNASSIGNED_REQUEST_ID = new CheckoutRequestId(UUID.randomUUID());
    private static final CheckoutId ASSIGNED_CHECKOUT_ID = new CheckoutId(UUID.randomUUID());

    private FakeSessions sessions;
    private FakeAssignments assignments;
    private FakeCheckoutReads reads;
    private CheckoutQueryService service;

    @BeforeEach
    void setUp() {
        sessions = new FakeSessions(new AuthenticatedSession(
                CUSTODIAN_ID, Role.EVIDENCE_CUSTODIAN, "Morgan Custodian"));
        assignments = new FakeAssignments();
        reads = new FakeCheckoutReads();
        service = new DefaultCheckoutQueryService(
                new NoOpTransactions(),
                new DefaultAuthorizationService(sessions, assignments),
                sessions,
                reads);
    }

    @Test
    void custodianReadsAreUnscopedAndMappedToPublicViews() {
        List<CheckoutViews.Request> requests = service.listRequests();
        CheckoutViews.Checkout checkout = service.getCheckout(ASSIGNED_CHECKOUT_ID);
        List<CheckoutViews.ExaminationNote> notes = service.listNotes(ASSIGNED_CHECKOUT_ID);

        assertEquals(List.of(ASSIGNED_REQUEST_ID, UNASSIGNED_REQUEST_ID), requests.stream()
                .map(CheckoutViews.Request::requestId)
                .toList());
        assertEquals("Assigned case", checkout.caseTitle());
        assertEquals("Observed a seal", notes.getFirst().text());
        assertTrue(reads.scopes.stream().allMatch(Optional::isEmpty));
    }

    @Test
    void assignedInvestigatorReadsUseTheCurrentSessionScope() {
        sessions.session = new AuthenticatedSession(
                ASSIGNED_INVESTIGATOR_ID, Role.INVESTIGATOR, "Alex Investigator");

        assertEquals(List.of(ASSIGNED_REQUEST_ID), service.listRequests().stream()
                .map(CheckoutViews.Request::requestId)
                .toList());
        assertEquals(ASSIGNED_CHECKOUT_ID, service.getCheckout(ASSIGNED_CHECKOUT_ID).checkoutId());
        assertEquals(ASSIGNED_INVESTIGATOR_ID, reads.scopes.getLast().orElseThrow());
    }

    @Test
    void directUnassignedAndApprovedCaseReadsDoNotExposeAView() {
        sessions.session = new AuthenticatedSession(
                ASSIGNED_INVESTIGATOR_ID, Role.INVESTIGATOR, "Alex Investigator");

        assertThrows(ServiceException.NotFound.class, () ->
                service.getRequest(UNASSIGNED_REQUEST_ID));
        assertEquals(List.of(), service.listNotes(new CheckoutId(UUID.randomUUID())));
    }

    @Test
    void revokedAssignmentRejectsCaseSpecificReadBeforeQueryingIt() {
        sessions.session = new AuthenticatedSession(
                ASSIGNED_INVESTIGATOR_ID, Role.INVESTIGATOR, "Alex Investigator");
        assignments.assigned = false;

        assertThrows(ServiceException.Forbidden.class, () ->
                service.listRequestsForCase(ASSIGNED_CASE_ID));
        assertTrue(reads.caseRequests.isEmpty());
    }

    private static final class NoOpTransactions implements TransactionRunner {
        @Override
        public <T> T inTransaction(TransactionalWork<T> work) {
            return work.execute(null);
        }
    }

    private static final class FakeSessions implements SessionProvider {
        private AuthenticatedSession session;

        FakeSessions(AuthenticatedSession session) {
            this.session = session;
        }

        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return Optional.ofNullable(session);
        }

        @Override
        public AuthenticatedSession requireSession() {
            if (session == null) {
                throw new ServiceException.Unauthenticated("A session is required");
            }
            return session;
        }
    }

    private static final class FakeAssignments implements AuthorizationRepository {
        private boolean assigned = true;

        @Override
        public boolean isAssigned(CaseId caseId, UserId investigatorId) {
            return assigned && caseId.equals(ASSIGNED_CASE_ID)
                    && investigatorId.equals(ASSIGNED_INVESTIGATOR_ID);
        }

        @Override
        public boolean isAssigned(Connection connection, CaseId caseId, UserId investigatorId) {
            return isAssigned(caseId, investigatorId);
        }

        @Override
        public boolean isCollectingInvestigator(CheckoutId checkoutId, UserId investigatorId) {
            return false;
        }

        @Override
        public boolean isCollectingInvestigator(
                Connection connection, CheckoutId checkoutId, UserId investigatorId) {
            return false;
        }
    }

    private static final class FakeCheckoutReads implements CheckoutReadRepository {
        private final List<Optional<UserId>> scopes = new java.util.ArrayList<>();
        private final List<CaseId> caseRequests = new java.util.ArrayList<>();
        private final RequestDetails assignedRequest = request(
                ASSIGNED_REQUEST_ID, ASSIGNED_CASE_ID, CheckoutRequestStatus.CONSUMED);
        private final RequestDetails approvedUnassignedRequest = request(
                UNASSIGNED_REQUEST_ID, UNASSIGNED_CASE_ID, CheckoutRequestStatus.APPROVED);
        private final CheckoutDetails checkout = checkout();
        private final ExaminationNoteDetails note = note();

        @Override
        public List<RequestDetails> listRequests(
                Connection connection,
                Optional<CheckoutRequestStatus> status,
                Optional<UserId> investigatorScope) {
            scopes.add(investigatorScope);
            return filterRequests(status, investigatorScope);
        }

        @Override
        public List<RequestDetails> listRequestsForCase(
                Connection connection, CaseId caseId, Optional<UserId> investigatorScope) {
            scopes.add(investigatorScope);
            caseRequests.add(caseId);
            return caseId.equals(ASSIGNED_CASE_ID) ? filterRequests(Optional.empty(), investigatorScope)
                    : List.of();
        }

        @Override
        public Optional<RequestDetails> findRequest(
                Connection connection,
                CheckoutRequestId requestId,
                Optional<UserId> investigatorScope) {
            scopes.add(investigatorScope);
            return filterRequests(Optional.empty(), investigatorScope).stream()
                    .filter(request -> request.requestId().equals(requestId))
                    .findFirst();
        }

        @Override
        public List<CheckoutDetails> listCheckouts(
                Connection connection, Optional<UserId> investigatorScope) {
            scopes.add(investigatorScope);
            return permitsAssignedCase(investigatorScope) ? List.of(checkout) : List.of();
        }

        @Override
        public List<CheckoutDetails> listCheckoutsForCase(
                Connection connection, CaseId caseId, Optional<UserId> investigatorScope) {
            scopes.add(investigatorScope);
            return caseId.equals(ASSIGNED_CASE_ID) && permitsAssignedCase(investigatorScope)
                    ? List.of(checkout) : List.of();
        }

        @Override
        public Optional<CheckoutDetails> findCheckout(
                Connection connection, CheckoutId checkoutId, Optional<UserId> investigatorScope) {
            scopes.add(investigatorScope);
            return checkout.checkoutId().equals(checkoutId) && permitsAssignedCase(investigatorScope)
                    ? Optional.of(checkout) : Optional.empty();
        }

        @Override
        public List<ExaminationNoteDetails> listNotes(
                Connection connection, CheckoutId checkoutId, Optional<UserId> investigatorScope) {
            scopes.add(investigatorScope);
            return checkout.checkoutId().equals(checkoutId) && permitsAssignedCase(investigatorScope)
                    ? List.of(note) : List.of();
        }

        private List<RequestDetails> filterRequests(
                Optional<CheckoutRequestStatus> status, Optional<UserId> scope) {
            return List.of(assignedRequest, approvedUnassignedRequest).stream()
                    .filter(request -> permits(request, scope))
                    .filter(request -> status.map(value -> value == request.status()).orElse(true))
                    .toList();
        }

        private static boolean permits(RequestDetails request, Optional<UserId> scope) {
            return scope.isEmpty() || request.caseId().equals(ASSIGNED_CASE_ID);
        }

        private static boolean permitsAssignedCase(Optional<UserId> scope) {
            return scope.isEmpty() || scope.orElseThrow().equals(ASSIGNED_INVESTIGATOR_ID);
        }

        private static RequestDetails request(
                CheckoutRequestId requestId, CaseId caseId, CheckoutRequestStatus status) {
            return new RequestDetails(
                    requestId, new EvidenceId(UUID.randomUUID()), "EV-TEST", caseId,
                    caseId.equals(ASSIGNED_CASE_ID) ? "Assigned case" : "Unassigned case",
                    ASSIGNED_INVESTIGATOR_ID, "Alex Investigator", "Review evidence",
                    NOW.plusSeconds(3600), status, EvidenceCustodyState.CHECKED_OUT, NOW,
                    Optional.of(new HandoffId(UUID.randomUUID())), Optional.empty());
        }

        private static CheckoutDetails checkout() {
            return new CheckoutDetails(
                    ASSIGNED_CHECKOUT_ID, ASSIGNED_REQUEST_ID, new EvidenceId(UUID.randomUUID()),
                    "EV-TEST", ASSIGNED_CASE_ID, "Assigned case", ASSIGNED_INVESTIGATOR_ID,
                    "Alex Investigator", NOW, Optional.empty(), Optional.empty(),
                    EvidenceCustodyState.CHECKED_OUT);
        }

        private static ExaminationNoteDetails note() {
            return new ExaminationNoteDetails(
                    new ExaminationNoteId(UUID.randomUUID()), ASSIGNED_CHECKOUT_ID,
                    ASSIGNED_INVESTIGATOR_ID, "Alex Investigator", "Observed a seal", NOW,
                    List.of(new NoteCorrectionDetails(
                            ASSIGNED_INVESTIGATOR_ID, "Alex Investigator", "Clarified seal",
                            "Typo", NOW.plusSeconds(1))));
        }
    }
}
