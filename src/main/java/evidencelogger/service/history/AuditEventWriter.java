package evidencelogger.service.history;

import java.sql.Connection;

import evidencelogger.domain.AuditEventId;

/** Appends immutable audit events using the caller's active transaction. */
public interface AuditEventWriter {
    /**
     * Appends an event without committing, rolling back, or closing the
     * runner-owned connection.
     */
    AuditEventId append(Connection connection, AuditEventDraft event);
}
