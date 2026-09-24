package evidencelogger.service.checkout;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.ReturnInspectionOutcome;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.repository.AuthorizationRepository;
import evidencelogger.repository.EvidenceRecord;
import evidencelogger.repository.EvidenceRepository;
import evidencelogger.repository.RepositoryException;
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

/** Transactional checkout commands for request and decision workflow actions. */
public final class DefaultCheckoutCommandService implements CheckoutCommandService {
    private final TransactionRunner transactions;
    private final AuthorizationService authorization;
    private final AuthorizationRepository authorizationRepository;
    private final EvidenceRepository evidenceRepository;
    private final CheckoutRequestRepository requestRepository;
    private final HandoffRepository handoffRepository;
    private final CheckoutRepository checkoutRepository;
    private final ExaminationNoteRepository noteRepository;
    private final ReturnInspectionRepository inspectionRepository;
    private final AuditEventWriter auditEvents;
    private final IdGenerator<CheckoutRequestId> requestIds;
    private final IdGenerator<HandoffId> handoffIds;
    private final IdGenerator<CheckoutId> checkoutIds;
    private final IdGenerator<ExaminationNoteId> noteIds;
    private final Clock clock;

    /** Creates checkout commands with all application collaborators injected. */
    public DefaultCheckoutCommandService(
            TransactionRunner transactions,
            AuthorizationService authorization,
            AuthorizationRepository authorizationRepository,
            EvidenceRepository evidenceRepository,
            CheckoutRequestRepository requestRepository,
            HandoffRepository handoffRepository,
            CheckoutRepository checkoutRepository,
            ExaminationNoteRepository noteRepository,
            ReturnInspectionRepository inspectionRepository,
            AuditEventWriter auditEvents,
            IdGenerator<CheckoutRequestId> requestIds,
            IdGenerator<HandoffId> handoffIds,
            IdGenerator<CheckoutId> checkoutIds,
            IdGenerator<ExaminationNoteId> noteIds,
            Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.authorizationRepository = Objects.requireNonNull(
                authorizationRepository, "authorizationRepository");
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "evidenceRepository");
        this.requestRepository = Objects.requireNonNull(requestRepository, "requestRepository");
        this.handoffRepository = Objects.requireNonNull(handoffRepository, "handoffRepository");
        this.checkoutRepository = Objects.requireNonNull(checkoutRepository, "checkoutRepository");
        this.noteRepository = Objects.requireNonNull(noteRepository, "noteRepository");
        this.inspectionRepository = Objects.requireNonNull(
                inspectionRepository, "inspectionRepository");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
        this.handoffIds = Objects.requireNonNull(handoffIds, "handoffIds");
        this.checkoutIds = Objects.requireNonNull(checkoutIds, "checkoutIds");
        this.noteIds = Objects.requireNonNull(noteIds, "noteIds");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CheckoutRequestId submitRequest(CheckoutCommands.SubmitRequest command) {
        Objects.requireNonNull(command, "command");
        return transactions.inTransaction(connection -> submit(connection, command));
    }

    private CheckoutRequestId submit(
            Connection connection, CheckoutCommands.SubmitRequest command) {
        Instant submittedAt = Instant.now(clock);
        if (!command.expectedReturnAt().isAfter(submittedAt)) {
            throw new ServiceException.ValidationFailure(
                    "Expected return time must be later than submission time");
        }

        EvidenceRecord evidence = findEvidence(connection, command.evidenceId());
        AuthenticatedSession actor = requireAssigned(connection, evidence);
        if (evidence.custodyState() != EvidenceCustodyState.IN_STORAGE) {
            throw new ServiceException.InvalidTransition(
                    "Evidence is not available in storage for a checkout request");
        }
        if (requestRepository.findPendingOrApprovedForEvidence(
                connection, evidence.evidenceId()).isPresent()) {
            throw new ServiceException.Conflict(
                    "Evidence already has an active checkout request");
        }

        CheckoutRequestId requestId = requestIds.nextId();
        CheckoutRequestRecord request = new CheckoutRequestRecord(
                requestId,
                evidence.evidenceId(),
                actor.userId(),
                command.purpose(),
                command.expectedReturnAt(),
                CheckoutRequestStatus.PENDING,
                submittedAt);
        try {
            requestRepository.insertPending(connection, request);
            auditEvents.append(connection, requestEvent(
                    AuditEventType.REQUEST_SUBMITTED,
                    evidence,
                    requestId,
                    Optional.empty(),
                    Optional.of(CheckoutRequestStatus.PENDING),
                    Optional.empty()));
            return requestId;
        } catch (RepositoryException exception) {
            throw translate(exception);
        }
    }

    @Override
    public void withdrawRequest(CheckoutCommands.WithdrawRequest command) {
        Objects.requireNonNull(command, "command");
        transactions.inTransaction(connection -> {
            withdraw(connection, command);
            return null;
        });
    }

    private void withdraw(Connection connection, CheckoutCommands.WithdrawRequest command) {
        CheckoutRequestRecord request = requestRepository.findById(connection, command.requestId())
                .orElseThrow(() -> new ServiceException.NotFound("Checkout request was not found"));
        EvidenceRecord evidence = findEvidence(connection, request.evidenceId());
        AuthenticatedSession actor = requireAssigned(connection, evidence);
        if (!request.requesterId().equals(actor.userId())) {
            throw new ServiceException.Forbidden(
                    "Only the requesting Investigator may withdraw this request");
        }
        if (request.status() != CheckoutRequestStatus.PENDING) {
            throw new ServiceException.InvalidTransition(
                    "Only a pending checkout request may be withdrawn");
        }
        try {
            if (!requestRepository.transitionStatus(
                    connection,
                    request.requestId(),
                    CheckoutRequestStatus.PENDING,
                    CheckoutRequestStatus.WITHDRAWN)) {
                throw new ServiceException.InvalidTransition(
                        "Checkout request is no longer pending");
            }
            auditEvents.append(connection, requestEvent(
                    AuditEventType.REQUEST_WITHDRAWN,
                    evidence,
                    request.requestId(),
                    Optional.of(CheckoutRequestStatus.PENDING),
                    Optional.of(CheckoutRequestStatus.WITHDRAWN),
                    Optional.empty()));
        } catch (RepositoryException exception) {
            throw translate(exception);
        }
    }

    @Override
    public void approveRequest(CheckoutCommands.ApproveRequest command) {
        Objects.requireNonNull(command, "command");
        transactions.inTransaction(connection -> {
            AuthenticatedSession custodian = authorization.requireCustodian();
            CheckoutRequestRecord request = findRequest(connection, command.requestId());
            EvidenceRecord evidence = findEvidence(connection, request.evidenceId());
            if (request.status() != CheckoutRequestStatus.PENDING) {
                throw new ServiceException.InvalidTransition(
                        "Only a pending checkout request may be approved");
            }
            if (evidence.custodyState() != EvidenceCustodyState.IN_STORAGE) {
                throw new ServiceException.InvalidTransition(
                        "Evidence is not available in storage for approval");
            }
            requireRequesterAssigned(connection, request, evidence);
            assertActiveRequestIs(request, connection);
            transitionAndAudit(
                    connection,
                    request,
                    evidence,
                    CheckoutRequestStatus.APPROVED,
                    AuditEventType.REQUEST_APPROVED,
                    Optional.empty());
            return custodian;
        });
    }

    @Override
    public void rejectRequest(CheckoutCommands.RejectRequest command) {
        Objects.requireNonNull(command, "command");
        transactions.inTransaction(connection -> {
            authorization.requireCustodian();
            CheckoutRequestRecord request = findRequest(connection, command.requestId());
            EvidenceRecord evidence = findEvidence(connection, request.evidenceId());
            if (request.status() != CheckoutRequestStatus.PENDING) {
                throw new ServiceException.InvalidTransition(
                        "Only a pending checkout request may be rejected");
            }
            transitionAndAudit(
                    connection,
                    request,
                    evidence,
                    CheckoutRequestStatus.REJECTED,
                    AuditEventType.REQUEST_REJECTED,
                    Optional.empty());
            return null;
        });
    }

    @Override
    public void cancelApprovedRequest(CheckoutCommands.CancelApprovedRequest command) {
        Objects.requireNonNull(command, "command");
        transactions.inTransaction(connection -> {
            authorization.requireCustodian();
            CheckoutRequestRecord request = findRequest(connection, command.requestId());
            EvidenceRecord evidence = findEvidence(connection, request.evidenceId());
            if (request.status() != CheckoutRequestStatus.APPROVED) {
                throw new ServiceException.InvalidTransition(
                        "Only an approved checkout request may be cancelled");
            }
            if (evidence.custodyState() != EvidenceCustodyState.IN_STORAGE) {
                throw new ServiceException.InvalidTransition(
                        "Evidence is not in storage for cancellation");
            }
            transitionAndAudit(
                    connection,
                    request,
                    evidence,
                    CheckoutRequestStatus.CANCELLED,
                    AuditEventType.REQUEST_CANCELLED,
                    Optional.of(command.reason()));
            return null;
        });
    }

    private CheckoutRequestRecord findRequest(
            Connection connection, CheckoutRequestId requestId) {
        return requestRepository.findById(connection, requestId)
                .orElseThrow(() -> new ServiceException.NotFound("Checkout request was not found"));
    }

    private void assertActiveRequestIs(
            CheckoutRequestRecord request, Connection connection) {
        requestRepository.findPendingOrApprovedForEvidence(connection, request.evidenceId())
                .filter(active -> !active.requestId().equals(request.requestId()))
                .ifPresent(active -> {
                    throw new ServiceException.Conflict(
                            "Evidence already has another active checkout request");
        });
    }

    private void requireRequesterAssigned(
            Connection connection, CheckoutRequestRecord request, EvidenceRecord evidence) {
        if (!authorizationRepository.isAssigned(
                connection, evidence.caseId(), request.requesterId())) {
            throw new ServiceException.Forbidden(
                    "The requesting Investigator is no longer assigned to this case");
        }
    }

    private void transitionAndAudit(
            Connection connection,
            CheckoutRequestRecord request,
            EvidenceRecord evidence,
            CheckoutRequestStatus resultingStatus,
            AuditEventType eventType,
            Optional<String> reason) {
        try {
            if (!requestRepository.transitionStatus(
                    connection,
                    request.requestId(),
                    request.status(),
                    resultingStatus)) {
                throw new ServiceException.InvalidTransition(
                        "Checkout request is no longer in the expected state");
            }
            auditEvents.append(connection, requestEvent(
                    eventType,
                    evidence,
                    request.requestId(),
                    Optional.of(request.status()),
                    Optional.of(resultingStatus),
                    reason));
        } catch (RepositoryException exception) {
            throw translate(exception);
        }
    }

    private EvidenceRecord findEvidence(Connection connection,
            evidencelogger.domain.EvidenceId evidenceId) {
        return evidenceRepository.findById(connection, evidenceId)
                .orElseThrow(() -> new ServiceException.NotFound("Evidence was not found"));
    }

    private CheckoutRecord findCheckout(Connection connection, CheckoutId checkoutId) {
        return checkoutRepository.findById(connection, checkoutId)
                .orElseThrow(() -> new ServiceException.NotFound("Checkout was not found"));
    }

    private AuthenticatedSession requireCollecting(
            Connection connection, CheckoutId checkoutId) {
        return authorization.requireCollectingInvestigator(
                checkoutId,
                (id, investigatorId) -> authorizationRepository.isCollectingInvestigator(
                        connection, id, investigatorId));
    }

    private AuthenticatedSession requireAssigned(
            Connection connection, EvidenceRecord evidence) {
        return authorization.requireAssignedInvestigator(
                evidence.caseId(),
                (caseId, investigatorId) -> authorizationRepository.isAssigned(
                        connection, caseId, investigatorId));
    }

    private static AuditEventDraft requestEvent(
            AuditEventType type,
            EvidenceRecord evidence,
            CheckoutRequestId requestId,
            Optional<CheckoutRequestStatus> previous,
            Optional<CheckoutRequestStatus> resulting,
            Optional<String> reason) {
        return new AuditEventDraft(
                type,
                Optional.of(evidence.caseId()),
                Optional.of(evidence.evidenceId()),
                Optional.of(requestId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                previous,
                resulting,
                Optional.of(evidence.custodyState()),
                Optional.of(evidence.custodyState()),
                reason,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static AuditEventDraft handoffEvent(
            AuditEventType type,
            EvidenceRecord evidence,
            CheckoutRequestId requestId,
            HandoffId handoffId,
            CheckoutRequestStatus previousRequestStatus,
            CheckoutRequestStatus resultingRequestStatus,
            EvidenceCustodyState previousCustodyState,
            EvidenceCustodyState resultingCustodyState,
            Optional<String> reason) {
        return new AuditEventDraft(
                type,
                Optional.of(evidence.caseId()),
                Optional.of(evidence.evidenceId()),
                Optional.of(requestId),
                Optional.of(handoffId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(previousRequestStatus),
                Optional.of(resultingRequestStatus),
                Optional.of(previousCustodyState),
                Optional.of(resultingCustodyState),
                reason,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static AuditEventDraft collectionEvent(
            EvidenceRecord evidence,
            CheckoutRequestId requestId,
            HandoffId handoffId,
            CheckoutId checkoutId) {
        return new AuditEventDraft(
                AuditEventType.COLLECTION_ACKNOWLEDGED,
                Optional.of(evidence.caseId()),
                Optional.of(evidence.evidenceId()),
                Optional.of(requestId),
                Optional.of(handoffId),
                Optional.of(checkoutId),
                Optional.empty(),
                Optional.empty(),
                Optional.of(CheckoutRequestStatus.APPROVED),
                Optional.of(CheckoutRequestStatus.CONSUMED),
                Optional.of(EvidenceCustodyState.HANDOFF_AWAITING_ACK),
                Optional.of(EvidenceCustodyState.CHECKED_OUT),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static AuditEventDraft noteEvent(
            AuditEventType type,
            EvidenceRecord evidence,
            CheckoutId checkoutId,
            Optional<ExaminationNoteId> noteId,
            Optional<String> correctionText,
            Optional<String> reason) {
        return noteEvent(type, evidence, checkoutId, noteId, correctionText, reason,
                Optional.empty());
    }

    private static AuditEventDraft noteEvent(
            AuditEventType type,
            EvidenceRecord evidence,
            CheckoutId checkoutId,
            Optional<ExaminationNoteId> noteId,
            Optional<String> correctionText,
            Optional<String> reason,
            Optional<ExaminationNoteId> correctedNoteId) {
        return new AuditEventDraft(
                type,
                Optional.of(evidence.caseId()),
                Optional.of(evidence.evidenceId()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(checkoutId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                reason,
                Optional.empty(),
                correctionText,
                Optional.empty(),
                correctedNoteId);
    }

    private static AuditEventDraft returnEvent(
            EvidenceRecord evidence,
            CheckoutId checkoutId,
            EvidenceCustodyState previousState,
            EvidenceCustodyState resultingState) {
        return new AuditEventDraft(
                AuditEventType.RETURN_INITIATED,
                Optional.of(evidence.caseId()),
                Optional.of(evidence.evidenceId()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(checkoutId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(previousState),
                Optional.of(resultingState),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static AuditEventDraft inspectionEvent(
            AuditEventType type,
            EvidenceRecord evidence,
            CheckoutId checkoutId,
            EvidenceCustodyState previousState,
            EvidenceCustodyState resultingState,
            Optional<String> reason) {
        return new AuditEventDraft(
                type,
                Optional.of(evidence.caseId()),
                Optional.of(evidence.evidenceId()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(checkoutId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(previousState),
                Optional.of(resultingState),
                reason,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static ServiceException translate(RepositoryException exception) {
        if (exception instanceof RepositoryException.Conflict) {
            return new ServiceException.Conflict(exception.getMessage());
        }
        if (exception instanceof RepositoryException.NotFound) {
            return new ServiceException.NotFound(exception.getMessage());
        }
        return new ServiceException.StorageFailure(exception.getMessage(), exception);
    }

    private static ServiceException notImplemented(String action) {
        return new ServiceException.InvalidTransition(
                action + " is not implemented yet");
    }

    @Override
    public HandoffId recordHandoff(CheckoutCommands.RecordHandoff command) {
        Objects.requireNonNull(command, "command");
        return transactions.inTransaction(connection -> {
            AuthenticatedSession custodian = authorization.requireCustodian();
            CheckoutRequestRecord request = findRequest(connection, command.requestId());
            EvidenceRecord evidence = findEvidence(connection, request.evidenceId());
            if (request.status() != CheckoutRequestStatus.APPROVED) {
                throw new ServiceException.InvalidTransition(
                        "Only an approved checkout request may be handed off");
            }
            if (evidence.custodyState() != EvidenceCustodyState.IN_STORAGE) {
                throw new ServiceException.InvalidTransition(
                        "Evidence is not available in storage for handoff");
            }
            requireRequesterAssigned(connection, request, evidence);
            if (handoffRepository.findUnacknowledgedForRequest(
                    connection, request.requestId()).isPresent()) {
                throw new ServiceException.Conflict(
                        "The request already has an unacknowledged handoff");
            }

            HandoffId handoffId = handoffIds.nextId();
            HandoffRecord handoff = new HandoffRecord(
                    handoffId,
                    request.requestId(),
                    evidence.evidenceId(),
                    custodian.userId(),
                    Instant.now(clock),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty());
            try {
                handoffRepository.insert(connection, handoff);
                auditEvents.append(connection, handoffEvent(
                        AuditEventType.HANDOFF_RECORDED,
                        evidence,
                        request.requestId(),
                        handoffId,
                        CheckoutRequestStatus.APPROVED,
                        CheckoutRequestStatus.APPROVED,
                        EvidenceCustodyState.IN_STORAGE,
                        EvidenceCustodyState.HANDOFF_AWAITING_ACK,
                        Optional.empty()));
                return handoffId;
            } catch (RepositoryException exception) {
                throw translate(exception);
            }
        });
    }

    @Override
    public void reverseHandoff(CheckoutCommands.ReverseHandoff command) {
        Objects.requireNonNull(command, "command");
        transactions.inTransaction(connection -> {
            authorization.requireCustodian();
            HandoffRecord handoff = handoffRepository.findById(connection, command.handoffId())
                    .orElseThrow(() -> new ServiceException.NotFound("Handoff was not found"));
            CheckoutRequestRecord request = findRequest(connection, handoff.requestId());
            EvidenceRecord evidence = findEvidence(connection, handoff.evidenceId());
            if (request.status() != CheckoutRequestStatus.APPROVED
                    || handoff.acknowledgedAt().isPresent()
                    || handoff.reversedAt().isPresent()
                    || evidence.custodyState() != EvidenceCustodyState.HANDOFF_AWAITING_ACK) {
                throw new ServiceException.InvalidTransition(
                        "Only an unacknowledged handoff may be reversed");
            }
            try {
                if (!handoffRepository.reverse(
                        connection,
                        handoff.handoffId(),
                        command.reason(),
                        Instant.now(clock))) {
                    throw new ServiceException.InvalidTransition(
                            "Handoff is no longer awaiting acknowledgment");
                }
                auditEvents.append(connection, handoffEvent(
                        AuditEventType.HANDOFF_REVERSED,
                        evidence,
                        request.requestId(),
                        handoff.handoffId(),
                        CheckoutRequestStatus.APPROVED,
                        CheckoutRequestStatus.CANCELLED,
                        EvidenceCustodyState.HANDOFF_AWAITING_ACK,
                        EvidenceCustodyState.IN_STORAGE,
                        Optional.of(command.reason())));
            } catch (RepositoryException exception) {
                throw translate(exception);
            }
            return null;
        });
    }

    @Override
    public CheckoutId acknowledgeCollection(
            CheckoutCommands.AcknowledgeCollection command) {
        Objects.requireNonNull(command, "command");
        return transactions.inTransaction(connection -> {
            HandoffRecord handoff = handoffRepository.findById(connection, command.handoffId())
                    .orElseThrow(() -> new ServiceException.NotFound("Handoff was not found"));
            CheckoutRequestRecord request = findRequest(connection, handoff.requestId());
            EvidenceRecord evidence = findEvidence(connection, handoff.evidenceId());
            AuthenticatedSession actor = requireAssigned(connection, evidence);
            if (!request.requesterId().equals(actor.userId())) {
                throw new ServiceException.Forbidden(
                        "Only the requesting Investigator may acknowledge collection");
            }
            if (request.status() != CheckoutRequestStatus.APPROVED
                    || handoff.acknowledgedAt().isPresent()
                    || handoff.reversedAt().isPresent()
                    || evidence.custodyState() != EvidenceCustodyState.HANDOFF_AWAITING_ACK) {
                throw new ServiceException.InvalidTransition(
                        "Only an approved unacknowledged handoff may be collected");
            }

            CheckoutId checkoutId = checkoutIds.nextId();
            Instant collectedAt = Instant.now(clock);
            try {
                if (!handoffRepository.acknowledge(
                        connection, handoff.handoffId(), collectedAt)) {
                    throw new ServiceException.InvalidTransition(
                            "Handoff is no longer awaiting acknowledgment");
                }
                checkoutRepository.insert(connection, new CheckoutRecord(
                        checkoutId,
                        request.requestId(),
                        evidence.evidenceId(),
                        actor.userId(),
                        collectedAt,
                        Optional.empty(),
                        Optional.empty(),
                        EvidenceCustodyState.CHECKED_OUT));
                auditEvents.append(connection, collectionEvent(
                        evidence,
                        request.requestId(),
                        handoff.handoffId(),
                        checkoutId));
                return checkoutId;
            } catch (RepositoryException exception) {
                throw translate(exception);
            }
        });
    }

    @Override
    public ExaminationNoteId addExaminationNote(
            CheckoutCommands.AddExaminationNote command) {
        Objects.requireNonNull(command, "command");
        return transactions.inTransaction(connection -> {
            CheckoutRecord checkout = findCheckout(connection, command.checkoutId());
            EvidenceRecord evidence = findEvidence(connection, checkout.evidenceId());
            AuthenticatedSession actor = requireCollecting(connection, checkout.checkoutId());
            if (checkout.returnInitiatedAt().isPresent()
                    || checkout.completedAt().isPresent()
                    || checkout.evidenceState() != EvidenceCustodyState.CHECKED_OUT
                    || evidence.custodyState() != EvidenceCustodyState.CHECKED_OUT) {
                throw new ServiceException.InvalidTransition(
                        "Examination notes are frozen for this checkout");
            }
            ExaminationNoteId noteId = noteIds.nextId();
            try {
                noteRepository.insert(connection, new ExaminationNoteRecord(
                        noteId,
                        checkout.checkoutId(),
                        actor.userId(),
                        command.text(),
                        Instant.now(clock)));
                auditEvents.append(connection, noteEvent(
                        AuditEventType.EXAMINATION_NOTE_ADDED,
                        evidence,
                        checkout.checkoutId(),
                        Optional.of(noteId),
                        Optional.empty(),
                        Optional.empty()));
                return noteId;
            } catch (RepositoryException exception) {
                throw translate(exception);
            }
        });
    }

    @Override
    public void correctExaminationNote(CheckoutCommands.CorrectExaminationNote command) {
        Objects.requireNonNull(command, "command");
        transactions.inTransaction(connection -> {
            ExaminationNoteRecord note = noteRepository.findById(connection, command.noteId())
                    .orElseThrow(() -> new ServiceException.NotFound("Examination note was not found"));
            CheckoutRecord checkout = findCheckout(connection, note.checkoutId());
            EvidenceRecord evidence = findEvidence(connection, checkout.evidenceId());
            AuthenticatedSession actor = authorization.requireAssignedInvestigator(
                    evidence.caseId(),
                    (caseId, investigatorId) -> authorizationRepository.isAssigned(
                            connection, caseId, investigatorId));
            if (!note.authorId().equals(actor.userId())) {
                throw new ServiceException.Forbidden(
                        "Only the original note author may append a correction");
            }
            try {
                noteRepository.appendCorrection(connection, new NoteCorrectionRecord(
                        note.noteId(),
                        actor.userId(),
                        command.correctionText(),
                        command.reason(),
                        Instant.now(clock)));
                auditEvents.append(connection, noteEvent(
                        AuditEventType.EXAMINATION_NOTE_CORRECTED,
                        evidence,
                        checkout.checkoutId(),
                        Optional.empty(),
                        Optional.of(command.correctionText()),
                        Optional.of(command.reason()),
                        Optional.of(note.noteId())));
            } catch (RepositoryException exception) {
                throw translate(exception);
            }
            return null;
        });
    }

    @Override
    public void initiateReturn(CheckoutCommands.InitiateReturn command) {
        Objects.requireNonNull(command, "command");
        transactions.inTransaction(connection -> {
            CheckoutRecord checkout = findCheckout(connection, command.checkoutId());
            EvidenceRecord evidence = findEvidence(connection, checkout.evidenceId());
            requireCollecting(connection, checkout.checkoutId());
            if (checkout.returnInitiatedAt().isPresent()
                    || checkout.completedAt().isPresent()
                    || checkout.evidenceState() != EvidenceCustodyState.CHECKED_OUT
                    || evidence.custodyState() != EvidenceCustodyState.CHECKED_OUT) {
                throw new ServiceException.InvalidTransition(
                        "Only an active checked-out item may be returned");
            }
            try {
                Instant initiatedAt = Instant.now(clock);
                if (!checkoutRepository.markReturnInitiated(
                        connection, checkout.checkoutId(), initiatedAt)) {
                    throw new ServiceException.InvalidTransition(
                            "Checkout return has already been initiated");
                }
                auditEvents.append(connection, returnEvent(
                        evidence,
                        checkout.checkoutId(),
                        EvidenceCustodyState.CHECKED_OUT,
                        EvidenceCustodyState.HANDIN_AWAITING_ACK));
            } catch (RepositoryException exception) {
                throw translate(exception);
            }
            return null;
        });
    }

    @Override
    public void inspectReturn(CheckoutCommands.InspectReturn command) {
        Objects.requireNonNull(command, "command");
        transactions.inTransaction(connection -> {
            AuthenticatedSession custodian = authorization.requireCustodian();
            CheckoutRecord checkout = findCheckout(connection, command.checkoutId());
            EvidenceRecord evidence = findEvidence(connection, checkout.evidenceId());
            if (command.outcome() != ReturnInspectionOutcome.STORED
                    || checkout.returnInitiatedAt().isEmpty()
                    || checkout.completedAt().isPresent()
                    || checkout.evidenceState() != EvidenceCustodyState.HANDIN_AWAITING_ACK
                    || evidence.custodyState() != EvidenceCustodyState.HANDIN_AWAITING_ACK) {
                throw new ServiceException.InvalidTransition(
                        "Only an initiated return may be inspected and stored");
            }
            try {
                inspectionRepository.insert(connection, new ReturnInspectionRecord(
                        checkout.checkoutId(),
                        custodian.userId(),
                        command.outcome(),
                        false,
                        "",
                        Instant.now(clock)));
                if (!checkoutRepository.complete(
                        connection, checkout.checkoutId(), Instant.now(clock))) {
                    throw new ServiceException.InvalidTransition(
                            "Return is no longer awaiting inspection");
                }
                auditEvents.append(connection, inspectionEvent(
                        AuditEventType.RETURN_INSPECTED_STORED,
                        evidence,
                        checkout.checkoutId(),
                        EvidenceCustodyState.HANDIN_AWAITING_ACK,
                        EvidenceCustodyState.IN_STORAGE,
                        Optional.empty()));
            } catch (RepositoryException exception) {
                throw translate(exception);
            }
            return null;
        });
    }

    @Override
    public void inspectUnplannedReturn(CheckoutCommands.InspectUnplannedReturn command) {
        Objects.requireNonNull(command, "command");
        transactions.inTransaction(connection -> {
            AuthenticatedSession custodian = authorization.requireCustodian();
            CheckoutRecord checkout = findCheckout(connection, command.checkoutId());
            EvidenceRecord evidence = findEvidence(connection, checkout.evidenceId());
            if (command.outcome() != ReturnInspectionOutcome.STORED
                    || checkout.returnInitiatedAt().isPresent()
                    || checkout.completedAt().isPresent()
                    || checkout.evidenceState() != EvidenceCustodyState.CHECKED_OUT
                    || evidence.custodyState() != EvidenceCustodyState.CHECKED_OUT) {
                throw new ServiceException.InvalidTransition(
                        "Only an active checked-out item may receive an unplanned return");
            }
            try {
                inspectionRepository.insert(connection, new ReturnInspectionRecord(
                        checkout.checkoutId(),
                        custodian.userId(),
                        command.outcome(),
                        true,
                        command.reason(),
                        Instant.now(clock)));
                if (!checkoutRepository.completeUnplanned(
                        connection, checkout.checkoutId(), Instant.now(clock))) {
                    throw new ServiceException.InvalidTransition(
                            "Checkout is no longer available for unplanned return");
                }
                auditEvents.append(connection, inspectionEvent(
                        AuditEventType.UNPLANNED_RETURN_INSPECTED,
                        evidence,
                        checkout.checkoutId(),
                        EvidenceCustodyState.CHECKED_OUT,
                        EvidenceCustodyState.IN_STORAGE,
                        Optional.of(command.reason())));
            } catch (RepositoryException exception) {
                throw translate(exception);
            }
            return null;
        });
    }
}
