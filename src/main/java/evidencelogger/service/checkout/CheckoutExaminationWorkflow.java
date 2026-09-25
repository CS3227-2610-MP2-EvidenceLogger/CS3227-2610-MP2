package evidencelogger.service.checkout;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.repository.EvidenceRecord;
import evidencelogger.repository.EvidenceRepository;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutRecord;
import evidencelogger.repository.checkout.CheckoutRepository;
import evidencelogger.repository.checkout.ExaminationNoteRecord;
import evidencelogger.repository.checkout.ExaminationNoteRepository;
import evidencelogger.repository.checkout.NoteCorrectionRecord;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.AuthorizationService;
import evidencelogger.service.dto.CheckoutCommands;
import evidencelogger.service.history.AuditEventDraft;
import evidencelogger.service.history.AuditEventWriter;

/** Coordinates examination-note additions and append-only corrections. */
final class CheckoutExaminationWorkflow {
    private final AuthorizationService authorization;
    private final EvidenceRepository evidenceRepository;
    private final CheckoutRepository checkoutRepository;
    private final ExaminationNoteRepository noteRepository;
    private final AuditEventWriter auditEvents;
    private final IdGenerator<ExaminationNoteId> noteIds;
    private final Clock clock;

    CheckoutExaminationWorkflow(
            AuthorizationService authorization,
            EvidenceRepository evidenceRepository,
            CheckoutRepository checkoutRepository,
            ExaminationNoteRepository noteRepository,
            AuditEventWriter auditEvents,
            IdGenerator<ExaminationNoteId> noteIds,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.evidenceRepository = Objects.requireNonNull(evidenceRepository, "evidenceRepository");
        this.checkoutRepository = Objects.requireNonNull(checkoutRepository, "checkoutRepository");
        this.noteRepository = Objects.requireNonNull(noteRepository, "noteRepository");
        this.auditEvents = Objects.requireNonNull(auditEvents, "auditEvents");
        this.noteIds = Objects.requireNonNull(noteIds, "noteIds");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    ExaminationNoteId addNote(
            Connection connection, CheckoutCommands.AddExaminationNote command) {
        CheckoutRecord checkout = findCheckout(connection, command.checkoutId());
        EvidenceRecord evidence = findEvidence(connection, checkout.evidenceId());
        AuthenticatedSession actor = authorization.requireCollectingInvestigator(
                connection, checkout.checkoutId());
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
            auditEvents.append(connection, actor, AuditEventDraft.examinationNoteChange(
                    AuditEventType.EXAMINATION_NOTE_ADDED,
                    evidence.caseId(),
                    evidence.evidenceId(),
                    checkout.checkoutId(),
                    Optional.of(noteId),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()));
            return noteId;
        } catch (RepositoryException exception) {
            throw CheckoutRepositoryErrors.translate(exception);
        }
    }

    void correctNote(
            Connection connection, CheckoutCommands.CorrectExaminationNote command) {
        ExaminationNoteRecord note = noteRepository.findById(connection, command.noteId())
                .orElseThrow(() -> new ServiceException.NotFound(
                        "Examination note was not found"));
        CheckoutRecord checkout = findCheckout(connection, note.checkoutId());
        EvidenceRecord evidence = findEvidence(connection, checkout.evidenceId());
        AuthenticatedSession actor = authorization.requireAssignedInvestigator(
                connection, evidence.caseId());
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
            auditEvents.append(connection, actor, AuditEventDraft.examinationNoteChange(
                    AuditEventType.EXAMINATION_NOTE_CORRECTED,
                    evidence.caseId(),
                    evidence.evidenceId(),
                    checkout.checkoutId(),
                    Optional.empty(),
                    Optional.of(command.correctionText()),
                    Optional.of(command.reason()),
                    Optional.of(note.noteId())));
        } catch (RepositoryException exception) {
            throw CheckoutRepositoryErrors.translate(exception);
        }
    }

    private CheckoutRecord findCheckout(Connection connection, CheckoutId checkoutId) {
        return checkoutRepository.findById(connection, checkoutId)
                .orElseThrow(() -> new ServiceException.NotFound("Checkout was not found"));
    }

    private EvidenceRecord findEvidence(
            Connection connection, evidencelogger.domain.EvidenceId evidenceId) {
        return evidenceRepository.findById(connection, evidenceId)
                .orElseThrow(() -> new ServiceException.NotFound("Evidence was not found"));
    }
}
