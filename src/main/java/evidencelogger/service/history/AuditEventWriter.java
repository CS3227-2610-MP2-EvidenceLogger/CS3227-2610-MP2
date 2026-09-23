package evidencelogger.service.history;

import evidencelogger.domain.AuditEventId;

/** Appends immutable audit events inside the caller's active transaction. */
public interface AuditEventWriter {
    AuditEventId append(AuditEventDraft event);
}
