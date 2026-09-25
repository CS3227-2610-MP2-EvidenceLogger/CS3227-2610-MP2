package evidencelogger.service.casework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.Role;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.ConnectionFactory;
import evidencelogger.infrastructure.db.JdbcTransactionRunner;
import evidencelogger.infrastructure.db.MigrationRunner;
import evidencelogger.infrastructure.db.SqliteConnectionFactory;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.repository.jdbc.JdbcAuditEventWriter;
import evidencelogger.repository.jdbc.JdbcAuthorizationRepository;
import evidencelogger.repository.jdbc.JdbcCaseworkRepository;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.DefaultAuthorizationService;
import evidencelogger.service.auth.SessionProvider;
import evidencelogger.service.dto.CaseworkCommands;
import evidencelogger.service.dto.CaseworkViews;
import evidencelogger.service.history.AuditEventWriter;

class DefaultCaseworkServiceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-24T10:15:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UserId CUSTODIAN_ID =
            UserId.parse("00000000-0000-0000-0000-000000000001");
    private static final UserId ALEX_ID =
            UserId.parse("00000000-0000-0000-0000-000000000002");
    private static final UserId BLAIR_ID =
            UserId.parse("00000000-0000-0000-0000-000000000003");

    @TempDir
    Path temporaryDirectory;

    private ConnectionFactory connectionFactory;
    private MutableSessionProvider sessions;
    private DefaultCaseworkService service;

    @BeforeEach
    void createCaseworkService() {
        connectionFactory = new SqliteConnectionFactory(
                temporaryDirectory.resolve("casework.db"));
        new MigrationRunner(connectionFactory, CLOCK).migrate();
        sessions = new MutableSessionProvider();
        sessions.set(CUSTODIAN_ID, Role.EVIDENCE_CUSTODIAN, "Morgan Custodian");
        service = newService(new JdbcAuditEventWriter(
                CLOCK, idGenerator(AuditEventId::new, 1000)));
    }

    @Test
    void custodianCreatesCaseAssignmentsLocationAndEvidenceWithRequiredAuditEvents()
            throws SQLException {
        CaseId caseId = service.createCase(
                new CaseworkCommands.CreateCase("  Phone inquiry  ", ALEX_ID));
        StorageLocationId locationId = service.addStorageLocation(
                new CaseworkCommands.AddStorageLocation("  Locker A  "));
        EvidenceId firstEvidenceId = service.registerEvidence(
                new CaseworkCommands.RegisterEvidence(
                        caseId, "  Mobile phone  ", locationId));
        EvidenceId secondEvidenceId = service.registerEvidence(
                new CaseworkCommands.RegisterEvidence(
                        caseId, "Charging cable", locationId));
        service.addAssignment(new CaseworkCommands.AddAssignment(caseId, BLAIR_ID));

        List<CaseworkViews.Case> cases = service.searchCases("phone");
        List<CaseworkViews.Evidence> evidence = service.searchEvidence("");

        assertEquals(1, cases.size());
        assertEquals("Phone inquiry", cases.getFirst().title());
        assertEquals(2, evidence.size());
        assertNotEquals(evidence.get(0).publicReference(), evidence.get(1).publicReference());
        assertEquals(EvidenceCustodyState.IN_STORAGE, evidence.getFirst().custodyState());
        assertEquals("Locker A", evidence.getFirst().storageLocationName());
        assertEquals(2, service.listAssignments(caseId).size());
        assertEquals(2, service.listInvestigators().size());
        assertEquals(1, service.listStorageLocations().size());
        assertThrows(ServiceException.Conflict.class, () -> service.addAssignment(
                new CaseworkCommands.AddAssignment(caseId, BLAIR_ID)));

        assertEquals(1, count("SELECT count(*) FROM audit_event WHERE event_type = 'CASE_CREATED'"));
        assertEquals(2, count("SELECT count(*) FROM audit_event WHERE event_type = 'CASE_ASSIGNED'"));
        assertEquals(1, count("SELECT count(*) FROM audit_event WHERE event_type = 'LOCATION_ADDED'"));
        assertEquals(2, count(
                "SELECT count(*) FROM audit_event WHERE event_type = 'EVIDENCE_REGISTERED'"));
        assertEvidenceAudit(firstEvidenceId, caseId, locationId);
        assertEvidenceAudit(secondEvidenceId, caseId, locationId);
    }

    @Test
    void unauthenticatedCallsRevealNoCaseworkData() {
        service.createCase(new CaseworkCommands.CreateCase("Private case", ALEX_ID));
        sessions.clear();

        assertThrows(ServiceException.Unauthenticated.class, () -> service.searchCases(""));
        assertThrows(ServiceException.Unauthenticated.class, () -> service.searchEvidence(""));
        assertThrows(ServiceException.Unauthenticated.class, () ->
                service.addStorageLocation(new CaseworkCommands.AddStorageLocation("Locker")));
    }

    @Test
    void investigatorQueriesAreConstrainedByCurrentAssignments() {
        CaseId alexCase = service.createCase(
                new CaseworkCommands.CreateCase("Alex case", ALEX_ID));
        CaseId blairCase = service.createCase(
                new CaseworkCommands.CreateCase("Blair case", BLAIR_ID));
        StorageLocationId locationId = service.addStorageLocation(
                new CaseworkCommands.AddStorageLocation("Locker B"));
        service.registerEvidence(new CaseworkCommands.RegisterEvidence(
                alexCase, "Alex item", locationId));
        service.registerEvidence(new CaseworkCommands.RegisterEvidence(
                blairCase, "Blair item", locationId));

        sessions.set(ALEX_ID, Role.INVESTIGATOR, "Alex Investigator");
        assertEquals(List.of("Alex case"), service.searchCases("").stream()
                .map(CaseworkViews.Case::title)
                .toList());
        assertEquals(List.of("Alex item"), service.searchEvidence("").stream()
                .map(CaseworkViews.Evidence::description)
                .toList());
        assertThrows(ServiceException.Forbidden.class, () ->
                service.addStorageLocation(new CaseworkCommands.AddStorageLocation("Forbidden")));
        assertThrows(ServiceException.Forbidden.class, service::listInvestigators);

        sessions.set(CUSTODIAN_ID, Role.EVIDENCE_CUSTODIAN, "Morgan Custodian");
        service.removeAssignment(new CaseworkCommands.RemoveAssignment(alexCase, ALEX_ID));
        sessions.set(ALEX_ID, Role.INVESTIGATOR, "Alex Investigator");
        assertEquals(0, service.searchCases("").size());
        assertEquals(0, service.searchEvidence("").size());
    }

    @Test
    void assignmentRemovalRejectsActiveRequestAndUninspectedCheckout()
            throws SQLException {
        CaseId caseId = service.createCase(
                new CaseworkCommands.CreateCase("Active work", ALEX_ID));
        StorageLocationId locationId = service.addStorageLocation(
                new CaseworkCommands.AddStorageLocation("Locker C"));
        EvidenceId evidenceId = service.registerEvidence(
                new CaseworkCommands.RegisterEvidence(caseId, "Active item", locationId));
        CheckoutRequestId requestId = CheckoutRequestId.parse(
                "00000000-0000-0000-0000-000000000501");
        insertPendingRequest(requestId, evidenceId);

        assertThrows(ServiceException.Conflict.class, () ->
                service.removeAssignment(
                        new CaseworkCommands.RemoveAssignment(caseId, ALEX_ID)));
        assertEquals(1, assignmentCount(caseId, ALEX_ID));
        assertEquals(0, unassignmentEventCount(caseId, ALEX_ID));

        HandoffId handoffId = HandoffId.parse(
                "00000000-0000-0000-0000-000000000502");
        createActiveCheckout(requestId, handoffId, evidenceId);
        assertThrows(ServiceException.Conflict.class, () ->
                service.removeAssignment(
                        new CaseworkCommands.RemoveAssignment(caseId, ALEX_ID)));
        assertEquals(1, assignmentCount(caseId, ALEX_ID));
        assertEquals(0, unassignmentEventCount(caseId, ALEX_ID));

        completeCheckout();
        service.removeAssignment(new CaseworkCommands.RemoveAssignment(caseId, ALEX_ID));
        assertEquals(0, assignmentCount(caseId, ALEX_ID));
        assertEquals(1, unassignmentEventCount(caseId, ALEX_ID));
    }

    @Test
    void validationAndDuplicateLocationFailuresLeaveNoPartialAudit() throws SQLException {
        assertThrows(ServiceException.ValidationFailure.class, () ->
                service.createCase(new CaseworkCommands.CreateCase("   ", ALEX_ID)));
        assertEquals(0, count("SELECT count(*) FROM case_record"));
        assertEquals(0, count("SELECT count(*) FROM audit_event"));

        service.addStorageLocation(new CaseworkCommands.AddStorageLocation("Locker D"));
        assertThrows(ServiceException.Conflict.class, () ->
                service.addStorageLocation(new CaseworkCommands.AddStorageLocation("locker d")));
        assertEquals(1, count("SELECT count(*) FROM storage_location"));
        assertEquals(1, count(
                "SELECT count(*) FROM audit_event WHERE event_type = 'LOCATION_ADDED'"));
    }

    @Test
    void auditFailureRollsBackCaseAssignmentAndEarlierAuditAppend() throws SQLException {
        JdbcAuditEventWriter delegate = new JdbcAuditEventWriter(
                CLOCK, idGenerator(AuditEventId::new, 2000));
        AtomicLong appendCount = new AtomicLong();
        AuditEventWriter failingWriter = (connection, actor, event) -> {
            if (appendCount.incrementAndGet() == 2) {
                throw new IllegalStateException("injected audit failure");
            }
            return delegate.append(connection, actor, event);
        };
        DefaultCaseworkService failingService = newService(failingWriter);

        assertThrows(IllegalStateException.class, () -> failingService.createCase(
                new CaseworkCommands.CreateCase("Rollback case", ALEX_ID)));

        assertEquals(0, count("SELECT count(*) FROM case_record"));
        assertEquals(0, count("SELECT count(*) FROM case_assignment"));
        assertEquals(0, count("SELECT count(*) FROM audit_event"));
    }

    @Test
    void sessionChangeDuringAuditDoesNotChangeAuthorizedActor() throws SQLException {
        JdbcAuditEventWriter delegate = new JdbcAuditEventWriter(
                CLOCK, idGenerator(AuditEventId::new, 3000));
        AuditEventWriter sessionChangingWriter = (connection, actor, event) -> {
            sessions.set(BLAIR_ID, Role.INVESTIGATOR, "Blair Investigator");
            return delegate.append(connection, actor, event);
        };
        DefaultCaseworkService sessionChangingService = newService(sessionChangingWriter);

        sessionChangingService.createCase(
                new CaseworkCommands.CreateCase("Actor binding", ALEX_ID));

        assertEquals(2, auditActorCount(CUSTODIAN_ID, Role.EVIDENCE_CUSTODIAN));
        assertEquals(0, auditActorCount(BLAIR_ID, Role.INVESTIGATOR));
    }

    private DefaultCaseworkService newService(AuditEventWriter auditEvents) {
        return new DefaultCaseworkService(
                new JdbcCaseworkRepository(connectionFactory),
                new JdbcTransactionRunner(connectionFactory),
                new DefaultAuthorizationService(
                        sessions, new JdbcAuthorizationRepository(connectionFactory)),
                sessions,
                auditEvents,
                CLOCK,
                idGenerator(CaseId::new, 100),
                idGenerator(StorageLocationId::new, 200),
                idGenerator(EvidenceId::new, 300));
    }

    private void assertEvidenceAudit(
            EvidenceId evidenceId, CaseId caseId, StorageLocationId locationId)
            throws SQLException {
        try (Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT actor_id, actor_role, event_time, case_id,
                               storage_location_id, resulting_custody_state
                        FROM audit_event
                        WHERE event_type = 'EVIDENCE_REGISTERED' AND evidence_id = ?
                        """)) {
            statement.setString(1, evidenceId.toString());
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                assertEquals(CUSTODIAN_ID.toString(), results.getString("actor_id"));
                assertEquals(Role.EVIDENCE_CUSTODIAN.name(), results.getString("actor_role"));
                assertEquals(NOW.toString(), results.getString("event_time"));
                assertEquals(caseId.toString(), results.getString("case_id"));
                assertEquals(locationId.toString(), results.getString("storage_location_id"));
                assertEquals(EvidenceCustodyState.IN_STORAGE.name(),
                        results.getString("resulting_custody_state"));
            }
        }
    }

    private void insertPendingRequest(
            CheckoutRequestId requestId, EvidenceId evidenceId) throws SQLException {
        try (Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO checkout_request(
                            id, evidence_id, requester_id, purpose,
                            expected_return_at, status, requested_at
                        ) VALUES (?, ?, ?, 'Review', ?, 'PENDING', ?)
                        """)) {
            statement.setString(1, requestId.toString());
            statement.setString(2, evidenceId.toString());
            statement.setString(3, ALEX_ID.toString());
            statement.setString(4, NOW.plusSeconds(3600).toString());
            statement.setString(5, NOW.toString());
            statement.executeUpdate();
        }
    }

    private void createActiveCheckout(
            CheckoutRequestId requestId, HandoffId handoffId, EvidenceId evidenceId)
            throws SQLException {
        try (Connection connection = connectionFactory.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement request = connection.prepareStatement(
                    "UPDATE checkout_request SET status = 'CONSUMED' WHERE id = ?");
                    PreparedStatement evidence = connection.prepareStatement(
                            "UPDATE evidence_item SET custody_state = 'CHECKED_OUT' WHERE id = ?");
                    PreparedStatement handoff = connection.prepareStatement("""
                            INSERT INTO handoff(
                                id, request_id, evidence_id, custodian_id,
                                recorded_at, acknowledged_at
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """);
                    PreparedStatement checkout = connection.prepareStatement("""
                            INSERT INTO checkout(
                                id, handoff_id, request_id, evidence_id,
                                collector_id, collected_at
                            ) VALUES ('00000000-0000-0000-0000-000000000503', ?, ?, ?, ?, ?)
                            """)) {
                request.setString(1, requestId.toString());
                request.executeUpdate();
                evidence.setString(1, evidenceId.toString());
                evidence.executeUpdate();
                handoff.setString(1, handoffId.toString());
                handoff.setString(2, requestId.toString());
                handoff.setString(3, evidenceId.toString());
                handoff.setString(4, CUSTODIAN_ID.toString());
                handoff.setString(5, NOW.toString());
                handoff.setString(6, NOW.toString());
                handoff.executeUpdate();
                checkout.setString(1, handoffId.toString());
                checkout.setString(2, requestId.toString());
                checkout.setString(3, evidenceId.toString());
                checkout.setString(4, ALEX_ID.toString());
                checkout.setString(5, NOW.toString());
                checkout.executeUpdate();
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private void completeCheckout() throws SQLException {
        try (Connection connection = connectionFactory.open();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE checkout
                    SET completed_at = '2026-09-24T11:15:30Z', return_outcome = 'STORED'
                    """);
        }
    }

    private int assignmentCount(CaseId caseId, UserId investigatorId) throws SQLException {
        try (Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT count(*) FROM case_assignment
                        WHERE case_id = ? AND investigator_id = ?
                        """)) {
            statement.setString(1, caseId.toString());
            statement.setString(2, investigatorId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.getInt(1);
            }
        }
    }

    private int unassignmentEventCount(CaseId caseId, UserId investigatorId)
            throws SQLException {
        try (Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT count(*) FROM audit_event
                        WHERE event_type = 'CASE_UNASSIGNED'
                          AND case_id = ? AND assigned_investigator_id = ?
                        """)) {
            statement.setString(1, caseId.toString());
            statement.setString(2, investigatorId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.getInt(1);
            }
        }
    }

    private int auditActorCount(UserId actorId, Role actorRole) throws SQLException {
        try (Connection connection = connectionFactory.open();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT count(*) FROM audit_event
                        WHERE actor_id = ? AND actor_role = ?
                        """)) {
            statement.setString(1, actorId.toString());
            statement.setString(2, actorRole.name());
            try (ResultSet results = statement.executeQuery()) {
                return results.getInt(1);
            }
        }
    }

    private int count(String sql) throws SQLException {
        try (Connection connection = connectionFactory.open();
                Statement statement = connection.createStatement();
                ResultSet results = statement.executeQuery(sql)) {
            return results.getInt(1);
        }
    }

    private static <T> IdGenerator<T> idGenerator(IdFactory<T> factory, long firstValue) {
        AtomicLong nextValue = new AtomicLong(firstValue);
        return () -> factory.create(new UUID(0L, nextValue.getAndIncrement()));
    }

    @FunctionalInterface
    private interface IdFactory<T> {
        T create(UUID value);
    }

    private static final class MutableSessionProvider implements SessionProvider {
        private AuthenticatedSession current;

        @Override
        public Optional<AuthenticatedSession> currentSession() {
            return Optional.ofNullable(current);
        }

        @Override
        public AuthenticatedSession requireSession() {
            if (current == null) {
                throw new ServiceException.Unauthenticated("Sign in is required");
            }
            return current;
        }

        private void set(UserId userId, Role role, String displayName) {
            current = new AuthenticatedSession(userId, role, displayName);
        }

        private void clear() {
            current = null;
        }
    }
}
