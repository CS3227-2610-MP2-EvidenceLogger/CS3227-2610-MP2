package evidencelogger.service.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.UserId;

/** Immutable checkout read models shared by both role views. */
public final class CheckoutViews {
    private CheckoutViews() {
    }

    public record Request(
            CheckoutRequestId requestId,
            EvidenceId evidenceId,
            String evidenceReference,
            CaseId caseId,
            String caseTitle,
            UserId requesterId,
            String requesterDisplayName,
            String purpose,
            Instant expectedReturnAt,
            CheckoutRequestStatus status,
            EvidenceCustodyState evidenceState,
            Instant submittedAt,
            Optional<HandoffId> handoffId,
            Optional<CheckoutId> checkoutId) {
        public Request {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(evidenceId, "evidenceId");
            Objects.requireNonNull(evidenceReference, "evidenceReference");
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(caseTitle, "caseTitle");
            Objects.requireNonNull(requesterId, "requesterId");
            Objects.requireNonNull(requesterDisplayName, "requesterDisplayName");
            Objects.requireNonNull(purpose, "purpose");
            Objects.requireNonNull(expectedReturnAt, "expectedReturnAt");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(evidenceState, "evidenceState");
            Objects.requireNonNull(submittedAt, "submittedAt");
            Objects.requireNonNull(handoffId, "handoffId");
            Objects.requireNonNull(checkoutId, "checkoutId");
        }
    }

    public record Checkout(
            CheckoutId checkoutId,
            CheckoutRequestId requestId,
            EvidenceId evidenceId,
            String evidenceReference,
            CaseId caseId,
            String caseTitle,
            UserId collectorId,
            String collectorDisplayName,
            Instant collectedAt,
            Optional<Instant> returnInitiatedAt,
            Optional<Instant> completedAt,
            EvidenceCustodyState evidenceState) {
        public Checkout {
            Objects.requireNonNull(checkoutId, "checkoutId");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(evidenceId, "evidenceId");
            Objects.requireNonNull(evidenceReference, "evidenceReference");
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(caseTitle, "caseTitle");
            Objects.requireNonNull(collectorId, "collectorId");
            Objects.requireNonNull(collectorDisplayName, "collectorDisplayName");
            Objects.requireNonNull(collectedAt, "collectedAt");
            Objects.requireNonNull(returnInitiatedAt, "returnInitiatedAt");
            Objects.requireNonNull(completedAt, "completedAt");
            Objects.requireNonNull(evidenceState, "evidenceState");
        }
    }

    public record ExaminationNote(
            ExaminationNoteId noteId,
            CheckoutId checkoutId,
            UserId authorId,
            String authorDisplayName,
            String text,
            Instant createdAt,
            List<NoteCorrection> corrections) {
        public ExaminationNote {
            Objects.requireNonNull(noteId, "noteId");
            Objects.requireNonNull(checkoutId, "checkoutId");
            Objects.requireNonNull(authorId, "authorId");
            Objects.requireNonNull(authorDisplayName, "authorDisplayName");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(createdAt, "createdAt");
            corrections = List.copyOf(corrections);
        }
    }

    public record NoteCorrection(
            UserId authorId,
            String authorDisplayName,
            String correctionText,
            String reason,
            Instant createdAt) {
        public NoteCorrection {
            Objects.requireNonNull(authorId, "authorId");
            Objects.requireNonNull(authorDisplayName, "authorDisplayName");
            Objects.requireNonNull(correctionText, "correctionText");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }
}
