package evidencelogger.service.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.ReturnInspectionOutcome;

/** Technology-neutral command inputs for the complete checkout workflow. */
public final class CheckoutCommands {
    private CheckoutCommands() {
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Optional<String> requireCommentForHeldOutcome(
            ReturnInspectionOutcome outcome,
            Optional<String> comment) {
        Objects.requireNonNull(comment, "comment");
        if (outcome == ReturnInspectionOutcome.HELD_FOR_REVIEW) {
            if (comment.isEmpty()) {
                throw new IllegalArgumentException(
                        "comment is required when evidence is held for review");
            }
            requireNonBlank(comment.orElseThrow(), "comment");
        }
        return comment;
    }

    public record SubmitRequest(EvidenceId evidenceId, String purpose, Instant expectedReturnAt) {
        public SubmitRequest {
            Objects.requireNonNull(evidenceId, "evidenceId");
            purpose = requireNonBlank(purpose, "purpose");
            Objects.requireNonNull(expectedReturnAt, "expectedReturnAt");
        }
    }

    public record WithdrawRequest(CheckoutRequestId requestId) {
        public WithdrawRequest {
            Objects.requireNonNull(requestId, "requestId");
        }
    }

    public record ApproveRequest(CheckoutRequestId requestId) {
        public ApproveRequest {
            Objects.requireNonNull(requestId, "requestId");
        }
    }

    public record RejectRequest(CheckoutRequestId requestId) {
        public RejectRequest {
            Objects.requireNonNull(requestId, "requestId");
        }
    }

    public record CancelApprovedRequest(CheckoutRequestId requestId, String reason) {
        public CancelApprovedRequest {
            Objects.requireNonNull(requestId, "requestId");
            reason = requireNonBlank(reason, "reason");
        }
    }

    public record RecordHandoff(CheckoutRequestId requestId) {
        public RecordHandoff {
            Objects.requireNonNull(requestId, "requestId");
        }
    }

    public record ReverseHandoff(HandoffId handoffId, String reason) {
        public ReverseHandoff {
            Objects.requireNonNull(handoffId, "handoffId");
            reason = requireNonBlank(reason, "reason");
        }
    }

    public record AcknowledgeCollection(HandoffId handoffId) {
        public AcknowledgeCollection {
            Objects.requireNonNull(handoffId, "handoffId");
        }
    }

    public record AddExaminationNote(CheckoutId checkoutId, String text) {
        public AddExaminationNote {
            Objects.requireNonNull(checkoutId, "checkoutId");
            text = requireNonBlank(text, "text");
        }
    }

    public record CorrectExaminationNote(ExaminationNoteId noteId, String correctionText, String reason) {
        public CorrectExaminationNote {
            Objects.requireNonNull(noteId, "noteId");
            correctionText = requireNonBlank(correctionText, "correctionText");
            reason = requireNonBlank(reason, "reason");
        }
    }

    public record InitiateReturn(CheckoutId checkoutId) {
        public InitiateReturn {
            Objects.requireNonNull(checkoutId, "checkoutId");
        }
    }

    public record InspectReturn(
            CheckoutId checkoutId,
            ReturnInspectionOutcome outcome,
            Optional<String> comment) {
        public InspectReturn {
            Objects.requireNonNull(checkoutId, "checkoutId");
            Objects.requireNonNull(outcome, "outcome");
            comment = requireCommentForHeldOutcome(outcome, comment);
        }
    }

    public record InspectUnplannedReturn(
            CheckoutId checkoutId,
            ReturnInspectionOutcome outcome,
            String reason,
            Optional<String> comment) {
        public InspectUnplannedReturn {
            Objects.requireNonNull(checkoutId, "checkoutId");
            Objects.requireNonNull(outcome, "outcome");
            reason = requireNonBlank(reason, "reason");
            comment = requireCommentForHeldOutcome(outcome, comment);
        }
    }
}
