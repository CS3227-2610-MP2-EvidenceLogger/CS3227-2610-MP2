package evidencelogger.repository.history;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;

/** Scoped, display-ready reads of immutable audit events. */
public interface AuditEventReadRepository {
    List<EventDetails> listEventsForCase(
            Connection connection, CaseId caseId, Optional<UserId> investigatorScope);

    /** Loads the subject links that a documentary correction must preserve. */
    Optional<EventSubjects> findEventSubjects(Connection connection, AuditEventId eventId);

    /** Subject links copied from the immutable event targeted by a correction. */
    record EventSubjects(
            Optional<CaseId> caseId,
            Optional<EvidenceId> evidenceId,
            Optional<CheckoutRequestId> requestId,
            Optional<HandoffId> handoffId,
            Optional<CheckoutId> checkoutId) {
        /** Validates explicit optional subject links. */
        public EventSubjects {
            Objects.requireNonNull(caseId, "caseId");
            Objects.requireNonNull(evidenceId, "evidenceId");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(handoffId, "handoffId");
            Objects.requireNonNull(checkoutId, "checkoutId");
        }
    }

    /** Immutable event details required by the authorized history view. */
    record EventDetails(
            AuditEventId eventId,
            AuditEventType type,
            UserId actorId,
            String actorDisplayName,
            Role actorRole,
            Instant eventTime,
            Optional<String> correctionText,
            Optional<String> reason,
            Optional<AuditEventId> correctedEventId) {
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
            Objects.requireNonNull(correctedEventId, "correctedEventId");
        }
    }
}
