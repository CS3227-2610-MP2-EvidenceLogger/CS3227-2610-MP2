package evidencelogger.service.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.Role;

/** Immutable audit-history read models shared by authorized role views. */
public final class HistoryViews {
    private HistoryViews() {
    }

    /** One append-only event shown in timestamp and stable-ID order. */
    public record Event(
            AuditEventId eventId,
            AuditEventType type,
            String actorDisplayName,
            Role actorRole,
            Instant eventTime,
            Optional<String> correctionText,
            Optional<String> reason) {
        /** Validates immutable display fields and explicit optional values. */
        public Event {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(actorDisplayName, "actorDisplayName");
            Objects.requireNonNull(actorRole, "actorRole");
            Objects.requireNonNull(eventTime, "eventTime");
            Objects.requireNonNull(correctionText, "correctionText");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
