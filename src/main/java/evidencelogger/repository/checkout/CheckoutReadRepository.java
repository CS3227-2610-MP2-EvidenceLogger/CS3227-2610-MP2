package evidencelogger.repository.checkout;

import java.sql.Connection;
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

/** Persistence reads that assemble the checkout details required by role views. */
public interface CheckoutReadRepository {
    List<RequestDetails> listRequests(
            Connection connection,
            Optional<CheckoutRequestStatus> status,
            Optional<UserId> investigatorScope);

    List<RequestDetails> listRequestsForCase(
            Connection connection, CaseId caseId, Optional<UserId> investigatorScope);

    Optional<RequestDetails> findRequest(
            Connection connection,
            CheckoutRequestId requestId,
            Optional<UserId> investigatorScope);

    List<CheckoutDetails> listCheckouts(
            Connection connection, Optional<UserId> investigatorScope);

    List<CheckoutDetails> listCheckoutsForCase(
            Connection connection, CaseId caseId, Optional<UserId> investigatorScope);

    Optional<CheckoutDetails> findCheckout(
            Connection connection, CheckoutId checkoutId, Optional<UserId> investigatorScope);

    List<ExaminationNoteDetails> listNotes(
            Connection connection, CheckoutId checkoutId, Optional<UserId> investigatorScope);

    /** Display-ready persisted details for one checkout request. */
    record RequestDetails(
            CheckoutRequestId requestId,
            EvidenceId evidenceId,
            String evidenceReference,
            String evidenceDescription,
            String storageLocationName,
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
        /** Validates the request display fields. */
        public RequestDetails {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(evidenceId, "evidenceId");
            Objects.requireNonNull(evidenceReference, "evidenceReference");
            Objects.requireNonNull(evidenceDescription, "evidenceDescription");
            Objects.requireNonNull(storageLocationName, "storageLocationName");
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

    /** Display-ready persisted details for one acknowledged checkout. */
    record CheckoutDetails(
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
        /** Validates the checkout display fields. */
        public CheckoutDetails {
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

    /** Display-ready persisted note details and their append-only corrections. */
    record ExaminationNoteDetails(
            ExaminationNoteId noteId,
            CheckoutId checkoutId,
            UserId authorId,
            String authorDisplayName,
            String text,
            Instant createdAt,
            List<NoteCorrectionDetails> corrections) {
        /** Validates note display fields and defensively copies corrections. */
        public ExaminationNoteDetails {
            Objects.requireNonNull(noteId, "noteId");
            Objects.requireNonNull(checkoutId, "checkoutId");
            Objects.requireNonNull(authorId, "authorId");
            Objects.requireNonNull(authorDisplayName, "authorDisplayName");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(createdAt, "createdAt");
            corrections = List.copyOf(corrections);
        }
    }

    /** Display-ready persisted details for one note correction. */
    record NoteCorrectionDetails(
            UserId authorId,
            String authorDisplayName,
            String correctionText,
            String reason,
            Instant createdAt) {
        /** Validates the note-correction display fields. */
        public NoteCorrectionDetails {
            Objects.requireNonNull(authorId, "authorId");
            Objects.requireNonNull(authorDisplayName, "authorDisplayName");
            Objects.requireNonNull(correctionText, "correctionText");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }
}
