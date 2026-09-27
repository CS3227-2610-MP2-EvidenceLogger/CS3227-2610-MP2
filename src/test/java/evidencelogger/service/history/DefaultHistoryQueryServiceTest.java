package evidencelogger.service.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.repository.AuthorizationRepository;
import evidencelogger.repository.history.AuditEventReadRepository;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.DefaultAuthorizationService;
import evidencelogger.service.auth.SessionProvider;
import evidencelogger.service.dto.HistoryViews;

class DefaultHistoryQueryServiceTest {
    private static final UserId CUSTODIAN_ID = new UserId(UUID.randomUUID());
    private static final UserId INVESTIGATOR_ID = new UserId(UUID.randomUUID());
    private static final CaseId ASSIGNED_CASE_ID = new CaseId(UUID.randomUUID());
    private static final CaseId UNASSIGNED_CASE_ID = new CaseId(UUID.randomUUID());
    private static final Instant EVENT_TIME = Instant.parse("2026-09-25T08:00:00Z");
    private static final AuditEventId EARLIER_EVENT_ID = AuditEventId.parse(
            "00000000-0000-0000-0000-000000000101");
    private static final AuditEventId LATER_EVENT_ID = AuditEventId.parse(
            "00000000-0000-0000-0000-000000000102");

    private FakeSessions sessions;
    private FakeAssignments assignments;
    private FakeHistoryReads reads;
    private HistoryQueryService service;

    @BeforeEach
    void setUp() {
        sessions = new FakeSessions(new AuthenticatedSession(
                CUSTODIAN_ID, Role.EVIDENCE_CUSTODIAN, "Morgan Custodian"));
        assignments = new FakeAssignments();
        reads = new FakeHistoryReads();
        service = new DefaultHistoryQueryService(
                new NoOpTransactions(),
                new DefaultAuthorizationService(sessions, assignments),
                sessions,
                reads);
    }

    @Test
    void investigatorHistoryUsesCurrentAssignmentScopeAndStableEventOrdering() {
        sessions.session = new AuthenticatedSession(
                INVESTIGATOR_ID, Role.INVESTIGATOR, "Alex Investigator");

        List<HistoryViews.Event> events = service.listEventsForCase(ASSIGNED_CASE_ID);

        assertEquals(List.of(EARLIER_EVENT_ID, LATER_EVENT_ID), events.stream()
                .map(HistoryViews.Event::eventId)
                .toList());
        assertEquals(AuditEventType.REQUEST_REJECTED, events.getFirst().type());
        assertEquals(Optional.of("EV-001"), events.getFirst().evidenceReference());
        assertEquals(Optional.of(CheckoutRequestStatus.REJECTED),
                events.getFirst().resultingRequestStatus());
        assertEquals("Insufficient purpose", events.getFirst().reason().orElseThrow());
        assertEquals(AuditEventType.EXAMINATION_NOTE_CORRECTED, events.getLast().type());
        assertEquals("Corrected identifier", events.getLast().correctionText().orElseThrow());
        assertEquals(Optional.of(INVESTIGATOR_ID), reads.lastScope);
    }

    @Test
    void unassignedOrRemovedInvestigatorCannotReadEarlierHistory() {
        sessions.session = new AuthenticatedSession(
                INVESTIGATOR_ID, Role.INVESTIGATOR, "Alex Investigator");

        assertThrows(ServiceException.Forbidden.class, () ->
                service.listEventsForCase(UNASSIGNED_CASE_ID));

        assignments.assigned = false;
        assertThrows(ServiceException.Forbidden.class, () ->
                service.listEventsForCase(ASSIGNED_CASE_ID));
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
            return assigned && ASSIGNED_CASE_ID.equals(caseId)
                    && INVESTIGATOR_ID.equals(investigatorId);
        }

        @Override
        public boolean isAssigned(Connection connection, CaseId caseId, UserId investigatorId) {
            return isAssigned(caseId, investigatorId);
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

    private static final class FakeHistoryReads implements AuditEventReadRepository {
        private Optional<UserId> lastScope = Optional.empty();

        @Override
        public List<EventDetails> listEventsForCase(
                Connection connection, CaseId caseId, Optional<UserId> investigatorScope) {
            lastScope = investigatorScope;
            if (!ASSIGNED_CASE_ID.equals(caseId)) {
                return List.of();
            }
            return List.of(
                    event(EARLIER_EVENT_ID, AuditEventType.REQUEST_REJECTED,
                            Optional.empty(), Optional.of("Insufficient purpose")),
                    event(LATER_EVENT_ID, AuditEventType.EXAMINATION_NOTE_CORRECTED,
                            Optional.of("Corrected identifier"), Optional.empty()));
        }

        private static EventDetails event(
                AuditEventId eventId,
                AuditEventType type,
                Optional<String> correctionText,
                Optional<String> reason) {
            return new EventDetails(
                    eventId, type, CUSTODIAN_ID, "Morgan Custodian",
                    Role.EVIDENCE_CUSTODIAN, EVENT_TIME,
                    Optional.of(new EvidenceId(UUID.randomUUID())),
                    Optional.of("EV-001"),
                    Optional.of(new CheckoutRequestId(UUID.randomUUID())),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(CheckoutRequestStatus.PENDING),
                    Optional.of(CheckoutRequestStatus.REJECTED),
                    Optional.of(EvidenceCustodyState.IN_STORAGE),
                    Optional.of(EvidenceCustodyState.IN_STORAGE),
                    correctionText, reason,
                    Optional.empty());
        }

        @Override
        public Optional<EventSubjects> findEventSubjects(
                Connection connection, AuditEventId eventId) {
            return Optional.empty();
        }
    }
}
