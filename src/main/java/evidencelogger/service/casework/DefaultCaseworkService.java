package evidencelogger.service.casework;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.Role;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.casework.CaseRecord;
import evidencelogger.repository.casework.CaseworkRepository;
import evidencelogger.repository.casework.EvidenceRecord;
import evidencelogger.repository.casework.InvestigatorRecord;
import evidencelogger.repository.casework.StorageLocationRecord;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.AuthorizationService;
import evidencelogger.service.auth.SessionProvider;
import evidencelogger.service.dto.CaseworkCommands;
import evidencelogger.service.dto.CaseworkViews;
import evidencelogger.service.history.AuditEventDraft;
import evidencelogger.service.history.AuditEventWriter;

/** Default authorized and transactional implementation of casework use cases. */
public final class DefaultCaseworkService
        implements CaseworkCommandService, CaseworkQueryService {
    private final CaseworkRepository casework;
    private final TransactionRunner transactions;
    private final AuthorizationService authorization;
    private final SessionProvider sessions;
    private final AuditEventWriter auditEvents;
    private final Clock clock;
    private final IdGenerator<CaseId> caseIds;
    private final IdGenerator<StorageLocationId> storageLocationIds;
    private final IdGenerator<EvidenceId> evidenceIds;

    /** Creates the casework service with its persistence and identity sources. */
    public DefaultCaseworkService(
            CaseworkRepository casework,
            TransactionRunner transactions,
            AuthorizationService authorization,
            SessionProvider sessions,
            AuditEventWriter auditEvents,
            Clock clock,
            IdGenerator<CaseId> caseIds,
            IdGenerator<StorageLocationId> storageLocationIds,
            IdGenerator<EvidenceId> evidenceIds) {
        this.casework = Objects.requireNonNull(casework, "casework");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.caseIds = Objects.requireNonNull(caseIds, "caseIds");
        this.storageLocationIds = Objects.requireNonNull(
                storageLocationIds, "storageLocationIds");
        this.evidenceIds = Objects.requireNonNull(evidenceIds, "evidenceIds");
    }

    @Override
    public CaseId createCase(CaseworkCommands.CreateCase command) {
        Objects.requireNonNull(command, "command");
        AuthenticatedSession actor = authorization.requireCustodian();
        String title = requireNonBlank(command.title(), "Case title");
        Instant now = Instant.now(clock);
        CaseId caseId = Objects.requireNonNull(caseIds.nextId(), "generated case ID");
        return runTransaction(connection -> {
            requireInvestigator(connection, command.initialInvestigatorId());
            casework.insertCase(connection, new CaseRecord(caseId, title, now));
            casework.insertAssignment(
                    connection, caseId, command.initialInvestigatorId(), now);
            auditEvents.append(
                    connection, actor, caseEvent(AuditEventType.CASE_CREATED, caseId));
            auditEvents.append(connection, actor, assignmentEvent(
                    AuditEventType.CASE_ASSIGNED, caseId, command.initialInvestigatorId()));
            return caseId;
        });
    }

    @Override
    public void addAssignment(CaseworkCommands.AddAssignment command) {
        Objects.requireNonNull(command, "command");
        AuthenticatedSession actor = authorization.requireCustodian();
        Instant now = Instant.now(clock);
        runTransaction(connection -> {
            requireCase(connection, command.caseId());
            requireInvestigator(connection, command.investigatorId());
            if (casework.assignmentExists(
                    connection, command.caseId(), command.investigatorId())) {
                throw new ServiceException.Conflict(
                        "The Investigator is already assigned to this case");
            }
            casework.insertAssignment(
                    connection, command.caseId(), command.investigatorId(), now);
            auditEvents.append(connection, actor, assignmentEvent(
                    AuditEventType.CASE_ASSIGNED,
                    command.caseId(),
                    command.investigatorId()));
            return null;
        });
    }

    @Override
    public void removeAssignment(CaseworkCommands.RemoveAssignment command) {
        Objects.requireNonNull(command, "command");
        AuthenticatedSession actor = authorization.requireCustodian();
        runTransaction(connection -> {
            requireCase(connection, command.caseId());
            requireInvestigator(connection, command.investigatorId());
            if (!casework.assignmentExists(
                    connection, command.caseId(), command.investigatorId())) {
                throw new ServiceException.NotFound(
                        "The Investigator is not assigned to this case");
            }
            if (!casework.removeAssignmentIfInactive(
                    connection, command.caseId(), command.investigatorId())) {
                throw new ServiceException.Conflict(
                        "The assignment cannot be removed while the Investigator has an active "
                                + "request or checkout for this case");
            }
            auditEvents.append(connection, actor, assignmentEvent(
                    AuditEventType.CASE_UNASSIGNED,
                    command.caseId(),
                    command.investigatorId()));
            return null;
        });
    }

    @Override
    public StorageLocationId addStorageLocation(
            CaseworkCommands.AddStorageLocation command) {
        Objects.requireNonNull(command, "command");
        AuthenticatedSession actor = authorization.requireCustodian();
        String name = requireNonBlank(command.name(), "Storage location name");
        Instant now = Instant.now(clock);
        StorageLocationId locationId = Objects.requireNonNull(
                storageLocationIds.nextId(), "generated storage location ID");
        return runTransaction(connection -> {
            casework.insertStorageLocation(
                    connection, new StorageLocationRecord(locationId, name, now));
            auditEvents.append(connection, actor, locationEvent(locationId));
            return locationId;
        });
    }

    @Override
    public EvidenceId registerEvidence(CaseworkCommands.RegisterEvidence command) {
        Objects.requireNonNull(command, "command");
        AuthenticatedSession actor = authorization.requireCustodian();
        String description = requireNonBlank(command.description(), "Evidence description");
        Instant now = Instant.now(clock);
        EvidenceId evidenceId = Objects.requireNonNull(
                evidenceIds.nextId(), "generated evidence ID");
        String publicReference = "EV-" + evidenceId;
        return runTransaction(connection -> {
            requireCase(connection, command.caseId());
            requireStorageLocation(connection, command.storageLocationId());
            casework.insertEvidence(
                    connection,
                    evidenceId,
                    command.caseId(),
                    publicReference,
                    description,
                    command.storageLocationId(),
                    EvidenceCustodyState.IN_STORAGE,
                    now);
            auditEvents.append(connection, actor, evidenceEvent(
                    command.caseId(), evidenceId, command.storageLocationId()));
            return evidenceId;
        });
    }

    @Override
    public void voidEvidence(CaseworkCommands.VoidEvidence command) {
        Objects.requireNonNull(command, "command");
        AuthenticatedSession actor = authorization.requireCustodian();
        String reason = requireNonBlank(command.reason(), "Void reason");
        runTransaction(connection -> {
            EvidenceRecord evidence = casework.findEvidence(connection, command.evidenceId())
                    .orElseThrow(() -> new ServiceException.NotFound(
                            "The evidence item does not exist"));
            if (!casework.voidEvidenceIfEligible(connection, command.evidenceId())) {
                throw new ServiceException.Conflict(
                        "Only in-storage evidence with no checkout request can be voided");
            }
            auditEvents.append(connection, actor, evidenceVoidEvent(evidence, reason));
            return null;
        });
    }

    @Override
    public List<CaseworkViews.Case> searchCases(String searchText) {
        AuthenticatedSession session = sessions.requireSession();
        Optional<UserId> investigatorId = queryScope(session);
        return runRead(() -> casework.searchCases(normalizeSearch(searchText), investigatorId))
                .stream()
                .map(DefaultCaseworkService::toView)
                .toList();
    }

    @Override
    public List<CaseworkViews.Evidence> searchEvidence(String searchText) {
        AuthenticatedSession session = sessions.requireSession();
        Optional<UserId> investigatorId = queryScope(session);
        return runRead(() -> casework.searchEvidence(
                normalizeSearch(searchText), investigatorId, false))
                .stream()
                .map(DefaultCaseworkService::toView)
                .toList();
    }

    @Override
    public List<CaseworkViews.Evidence> searchEvidenceIncludingVoided(String searchText) {
        authorization.requireCustodian();
        return runRead(() -> casework.searchEvidence(
                normalizeSearch(searchText), Optional.empty(), true))
                .stream()
                .map(DefaultCaseworkService::toView)
                .toList();
    }

    @Override
    public List<CaseworkViews.Investigator> listInvestigators() {
        authorization.requireCustodian();
        return runRead(casework::listInvestigators).stream()
                .map(DefaultCaseworkService::toView)
                .toList();
    }

    @Override
    public List<CaseworkViews.Investigator> listAssignments(CaseId caseId) {
        Objects.requireNonNull(caseId, "caseId");
        authorization.requireCustodian();
        return runRead(() -> casework.listAssignments(caseId)).stream()
                .map(DefaultCaseworkService::toView)
                .toList();
    }

    @Override
    public List<CaseworkViews.StorageLocation> listStorageLocations() {
        authorization.requireCustodian();
        return runRead(casework::listStorageLocations).stream()
                .map(DefaultCaseworkService::toView)
                .toList();
    }

    private void requireCase(Connection connection, CaseId caseId) {
        if (!casework.caseExists(connection, caseId)) {
            throw new ServiceException.NotFound("The case does not exist");
        }
    }

    private void requireInvestigator(Connection connection, UserId investigatorId) {
        if (!casework.investigatorExists(connection, investigatorId)) {
            throw new ServiceException.NotFound("The Investigator does not exist");
        }
    }

    private void requireStorageLocation(
            Connection connection, StorageLocationId storageLocationId) {
        if (!casework.storageLocationExists(connection, storageLocationId)) {
            throw new ServiceException.NotFound("The storage location does not exist");
        }
    }

    private <T> T runTransaction(TransactionRunner.TransactionalWork<T> work) {
        try {
            return transactions.inTransaction(work);
        } catch (RepositoryException.Conflict exception) {
            throw new ServiceException.Conflict(exception.getMessage());
        } catch (RepositoryException.NotFound exception) {
            throw new ServiceException.NotFound(exception.getMessage());
        } catch (RepositoryException.StorageFailure exception) {
            throw new ServiceException.StorageFailure(
                    "Casework data could not be changed", exception);
        }
    }

    private static <T> T runRead(Supplier<T> query) {
        try {
            return query.get();
        } catch (RepositoryException.StorageFailure exception) {
            throw new ServiceException.StorageFailure(
                    "Casework data could not be read", exception);
        }
    }

    private static Optional<UserId> queryScope(AuthenticatedSession session) {
        if (session.role() == Role.EVIDENCE_CUSTODIAN) {
            return Optional.empty();
        }
        if (session.role() == Role.INVESTIGATOR) {
            return Optional.of(session.userId());
        }
        throw new ServiceException.Forbidden(
                "The signed-in role cannot read casework data");
    }

    private static String normalizeSearch(String searchText) {
        return searchText == null ? "" : searchText.strip();
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ServiceException.ValidationFailure(fieldName + " is required");
        }
        return value.strip();
    }

    private static CaseworkViews.Case toView(CaseRecord record) {
        return new CaseworkViews.Case(
                record.caseId(), record.title(), record.createdAt());
    }

    private static CaseworkViews.Investigator toView(InvestigatorRecord record) {
        return new CaseworkViews.Investigator(
                record.investigatorId(), record.username(), record.displayName());
    }

    private static CaseworkViews.StorageLocation toView(StorageLocationRecord record) {
        return new CaseworkViews.StorageLocation(
                record.storageLocationId(), record.name(), record.createdAt());
    }

    private static CaseworkViews.Evidence toView(EvidenceRecord record) {
        return new CaseworkViews.Evidence(
                record.evidenceId(),
                record.caseId(),
                record.caseTitle(),
                record.publicReference(),
                record.description(),
                record.storageLocationId(),
                record.storageLocationName(),
                record.custodyState(),
                record.registeredAt());
    }

    private static AuditEventDraft caseEvent(AuditEventType type, CaseId caseId) {
        return auditEvent(
                type,
                Optional.of(caseId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static AuditEventDraft assignmentEvent(
            AuditEventType type, CaseId caseId, UserId investigatorId) {
        return auditEvent(
                type,
                Optional.of(caseId),
                Optional.empty(),
                Optional.of(investigatorId),
                Optional.empty());
    }

    private static AuditEventDraft locationEvent(StorageLocationId storageLocationId) {
        return auditEvent(
                AuditEventType.LOCATION_ADDED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(storageLocationId));
    }

    private static AuditEventDraft evidenceEvent(
            CaseId caseId, EvidenceId evidenceId, StorageLocationId storageLocationId) {
        return new AuditEventDraft(
                AuditEventType.EVIDENCE_REGISTERED,
                Optional.of(caseId),
                Optional.of(evidenceId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(storageLocationId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(EvidenceCustodyState.IN_STORAGE),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static AuditEventDraft evidenceVoidEvent(
            EvidenceRecord evidence, String reason) {
        return new AuditEventDraft(
                AuditEventType.EVIDENCE_VOIDED,
                Optional.of(evidence.caseId()),
                Optional.of(evidence.evidenceId()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(evidence.storageLocationId()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(EvidenceCustodyState.IN_STORAGE),
                Optional.of(EvidenceCustodyState.VOIDED),
                Optional.of(reason),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static AuditEventDraft auditEvent(
            AuditEventType type,
            Optional<CaseId> caseId,
            Optional<EvidenceId> evidenceId,
            Optional<UserId> investigatorId,
            Optional<StorageLocationId> storageLocationId) {
        return new AuditEventDraft(
                type,
                caseId,
                evidenceId,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                investigatorId,
                storageLocationId,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
