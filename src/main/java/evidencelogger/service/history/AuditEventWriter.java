package evidencelogger.service.history;

import java.sql.Connection;

import evidencelogger.domain.AuditEventId;
import evidencelogger.service.auth.AuthenticatedSession;

/** Appends immutable audit events using the caller's active transaction. */
public interface AuditEventWriter {
    /**
     * Appends an event for the session authorized by the calling service,
     * without committing, rolling back, or closing the runner-owned connection.
     */
    AuditEventId append(
            Connection connection, AuthenticatedSession actor, AuditEventDraft event);
}
