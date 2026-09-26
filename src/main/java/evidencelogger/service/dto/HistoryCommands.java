package evidencelogger.service.dto;

import java.util.Objects;

import evidencelogger.domain.AuditEventId;

/** Technology-neutral command inputs for append-only documentary corrections. */
public final class HistoryCommands {
    private HistoryCommands() {
    }

    /** Input for appending a correction linked to an existing audit event. */
    public record CorrectEvent(AuditEventId eventId, String correctionText, String reason) {
        /** Validates the target, correction text, and reason. */
        public CorrectEvent {
            Objects.requireNonNull(eventId, "eventId");
            correctionText = requireNonBlank(correctionText, "correctionText");
            reason = requireNonBlank(reason, "reason");
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
