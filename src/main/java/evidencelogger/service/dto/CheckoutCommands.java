package evidencelogger.service.dto;

import java.time.Instant;
import java.util.Objects;

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

    /** Input for submitting an evidence checkout request. */
    public record SubmitRequest(EvidenceId evidenceId, String purpose, Instant expectedReturnAt) {
        /** Validates the required request details. */
        public SubmitRequest {
            Objects.requireNonNull(evidenceId, "evidenceId");
            purpose = requireNonBlank(purpose, "purpose");
            Objects.requireNonNull(expectedReturnAt, "expectedReturnAt");
        }
    }

    /** Input for withdrawing a pending checkout request. */
    public record WithdrawRequest(CheckoutRequestId requestId) {
        public WithdrawRequest {
            Objects.requireNonNull(requestId, "requestId");
        }
    }

    /** Input for approving a pending checkout request. */
    public record ApproveRequest(CheckoutRequestId requestId) {
        public ApproveRequest {
            Objects.requireNonNull(requestId, "requestId");
        }
    }

    /** Input for rejecting a pending checkout request. */
    public record RejectRequest(CheckoutRequestId requestId) {
        public RejectRequest {
            Objects.requireNonNull(requestId, "requestId");
        }
    }

    /** Input for cancelling a previously approved request. */
    public record CancelApprovedRequest(CheckoutRequestId requestId, String reason) {
        /** Validates the request identifier and cancellation reason. */
        public CancelApprovedRequest {
            Objects.requireNonNull(requestId, "requestId");
            reason = requireNonBlank(reason, "reason");
        }
    }

    /** Input for recording the handoff of approved evidence. */
    public record RecordHandoff(CheckoutRequestId requestId) {
        public RecordHandoff {
            Objects.requireNonNull(requestId, "requestId");
        }
    }

    /** Input for reversing an evidence handoff. */
    public record ReverseHandoff(HandoffId handoffId, String reason) {
        /** Validates the handoff identifier and reversal reason. */
        public ReverseHandoff {
            Objects.requireNonNull(handoffId, "handoffId");
            reason = requireNonBlank(reason, "reason");
        }
    }

    /** Input for acknowledging collection of handed-off evidence. */
    public record AcknowledgeCollection(HandoffId handoffId) {
        public AcknowledgeCollection {
            Objects.requireNonNull(handoffId, "handoffId");
        }
    }

    /** Input for adding an examination note to an active checkout. */
    public record AddExaminationNote(CheckoutId checkoutId, String text) {
        /** Validates the checkout identifier and note text. */
        public AddExaminationNote {
            Objects.requireNonNull(checkoutId, "checkoutId");
            text = requireNonBlank(text, "text");
        }
    }

    /** Input for appending a correction to an examination note. */
    public record CorrectExaminationNote(ExaminationNoteId noteId, String correctionText, String reason) {
        /** Validates the target note, correction text, and reason. */
        public CorrectExaminationNote {
            Objects.requireNonNull(noteId, "noteId");
            correctionText = requireNonBlank(correctionText, "correctionText");
            reason = requireNonBlank(reason, "reason");
        }
    }

    /** Input for initiating the return of checked-out evidence. */
    public record InitiateReturn(CheckoutId checkoutId) {
        public InitiateReturn {
            Objects.requireNonNull(checkoutId, "checkoutId");
        }
    }

    /** Input for inspecting an expected evidence return. */
    public record InspectReturn(
            CheckoutId checkoutId,
            ReturnInspectionOutcome outcome) {
        /** Validates the checkout and inspection outcome. */
        public InspectReturn {
            Objects.requireNonNull(checkoutId, "checkoutId");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /** Input for inspecting evidence returned without a planned return. */
    public record InspectUnplannedReturn(
            CheckoutId checkoutId,
            ReturnInspectionOutcome outcome,
            String reason) {
        /** Validates the checkout, outcome, and reason. */
        public InspectUnplannedReturn {
            Objects.requireNonNull(checkoutId, "checkoutId");
            Objects.requireNonNull(outcome, "outcome");
            reason = requireNonBlank(reason, "reason");
        }
    }
}
