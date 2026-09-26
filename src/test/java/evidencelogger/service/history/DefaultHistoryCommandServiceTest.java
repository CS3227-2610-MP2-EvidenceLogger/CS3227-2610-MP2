package evidencelogger.service.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.repository.AuthorizationRepository;
import evidencelogger.repository.history.AuditEventReadRepository;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.DefaultAuthorizationService;
import evidencelogger.service.auth.SessionProvider;
import evidencelogger.service.dto.HistoryCommands;

class DefaultHistoryCommandServiceTest {
    private static final UserId CUSTODIAN_ID = UserId.parse(
            "00000000-0000-0000-0000-000000000001");
    private static final UserId INVESTIGATOR_ID = UserId.parse(
            "00000000-0000-0000-0000-000000000002");
    private static final CaseId CASE_ID = CaseId.parse(
            "00000000-0000-0000-0000-000000000801");
    private static final AuditEventId TARGET_ID = AuditEventId.parse(
            "00000000-0000-0000-0000-000000000802");

    private FakeSessions sessions;
    private FakeEvents events;
    private RecordingWriter writer;
    private HistoryCommandService service;

    @BeforeEach
    void setUp() {
        sessions = new FakeSessions(new AuthenticatedSession(
                CUSTODIAN_ID, Role.EVIDENCE_CUSTODIAN, "Morgan Custodian"));
        events = new FakeEvents();
        writer = new RecordingWriter();
        service = new DefaultHistoryCommandService(
                new NoOpTransactions(),
                new DefaultAuthorizationService(sessions, new NoAssignments()),
                events,
                writer);
    }

    @Test
    void custodianAppendsLinkedCorrectionWithOriginalSubjects() {
        service.correctEvent(new HistoryCommands.CorrectEvent(
                TARGET_ID, "Corrected description", "Original was inaccurate"));

        assertEquals(TARGET_ID, events.requestedId);
        assertEquals(CUSTODIAN_ID, writer.actor.userId());
        assertEquals(AuditEventType.HISTORY_CORRECTED, writer.event.type());
        assertEquals(Optional.of(CASE_ID), writer.event.caseId());
        assertEquals(Optional.of(TARGET_ID), writer.event.correctedEventId());
        assertEquals(Optional.of("Corrected description"), writer.event.correctionText());
        assertEquals(Optional.of("Original was inaccurate"), writer.event.reason());
    }

    @Test
    void investigatorCannotAppendDocumentaryHistoryCorrection() {
        sessions.session = new AuthenticatedSession(
                INVESTIGATOR_ID, Role.INVESTIGATOR, "Alex Investigator");

        assertThrows(ServiceException.Forbidden.class, () -> service.correctEvent(
                new HistoryCommands.CorrectEvent(TARGET_ID, "Correction", "Reason")));
        assertEquals(null, writer.event);
    }

    @Test
    void missingTargetDoesNotAppendCorrection() {
        events.target = Optional.empty();

        assertThrows(ServiceException.NotFound.class, () -> service.correctEvent(
                new HistoryCommands.CorrectEvent(TARGET_ID, "Correction", "Reason")));
        assertEquals(null, writer.event);
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

    private static final class NoAssignments implements AuthorizationRepository {
        @Override
        public boolean isAssigned(CaseId caseId, UserId investigatorId) {
            return false;
        }

        @Override
        public boolean isAssigned(Connection connection, CaseId caseId, UserId investigatorId) {
            return false;
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

    private static final class FakeEvents implements AuditEventReadRepository {
        private AuditEventId requestedId;
        private Optional<EventSubjects> target = Optional.of(new EventSubjects(
                Optional.of(CASE_ID),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()));

        @Override
        public List<EventDetails> listEventsForCase(
                Connection connection, CaseId caseId, Optional<UserId> investigatorScope) {
            return List.of();
        }

        @Override
        public Optional<EventSubjects> findEventSubjects(
                Connection connection, AuditEventId eventId) {
            requestedId = eventId;
            return target;
        }
    }

    private static final class RecordingWriter implements AuditEventWriter {
        private AuthenticatedSession actor;
        private AuditEventDraft event;

        @Override
        public AuditEventId append(
                Connection connection,
                AuthenticatedSession authenticatedActor,
                AuditEventDraft eventDraft) {
            actor = authenticatedActor;
            event = eventDraft;
            return new AuditEventId(java.util.UUID.randomUUID());
        }
    }
}
