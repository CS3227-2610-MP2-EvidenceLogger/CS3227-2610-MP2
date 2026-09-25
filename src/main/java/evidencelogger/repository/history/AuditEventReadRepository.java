package evidencelogger.repository.history;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;

/** Scoped, display-ready reads of immutable audit events. */
public interface AuditEventReadRepository {
    List<EventDetails> listEventsForCase(
            Connection connection, CaseId caseId, Optional<UserId> investigatorScope);

    /** Immutable event details required by the authorized history view. */
    record EventDetails(
            AuditEventId eventId,
            AuditEventType type,
            UserId actorId,
            String actorDisplayName,
            Role actorRole,
            Instant eventTime,
            Optional<String> correctionText,
            Optional<String> reason) {
        /** Validates event display fields and explicit optional values. */
        public EventDetails {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(actorDisplayName, "actorDisplayName");
            Objects.requireNonNull(actorRole, "actorRole");
            Objects.requireNonNull(eventTime, "eventTime");
            Objects.requireNonNull(correctionText, "correctionText");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
