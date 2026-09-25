package evidencelogger.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.AuditEventId;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.history.AuditEventDraft;
import evidencelogger.service.history.AuditEventWriter;

/** Appends audit events using the authorized actor and caller-owned transaction. */
public final class JdbcAuditEventWriter implements AuditEventWriter {
    private final Clock clock;
    private final IdGenerator<AuditEventId> eventIds;

    /** Creates a writer with the shared clock and event-ID source. */
    public JdbcAuditEventWriter(
            Clock clock,
            IdGenerator<AuditEventId> eventIds) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.eventIds = Objects.requireNonNull(eventIds, "eventIds");
    }

    @Override
    public AuditEventId append(
            Connection connection, AuthenticatedSession actor, AuditEventDraft event) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(event, "event");
        AuditEventId eventId = Objects.requireNonNull(eventIds.nextId(), "generated event ID");
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO audit_event(
                    id, event_type, actor_id, actor_role, event_time,
                    case_id, evidence_id, request_id, handoff_id, checkout_id,
                    assigned_investigator_id, storage_location_id,
                    previous_request_status, resulting_request_status,
                    previous_custody_state, resulting_custody_state,
                    reason, comment, correction_text, corrected_event_id, corrected_note_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, eventId.toString());
            statement.setString(2, event.type().name());
            statement.setString(3, actor.userId().toString());
            statement.setString(4, actor.role().name());
            statement.setString(5, Instant.now(clock).toString());
            setOptional(statement, 6, event.caseId());
            setOptional(statement, 7, event.evidenceId());
            setOptional(statement, 8, event.requestId());
            setOptional(statement, 9, event.handoffId());
            setOptional(statement, 10, event.checkoutId());
            setOptional(statement, 11, event.assignedInvestigatorId());
            setOptional(statement, 12, event.storageLocationId());
            setOptional(statement, 13, event.previousRequestStatus());
            setOptional(statement, 14, event.resultingRequestStatus());
            setOptional(statement, 15, event.previousCustodyState());
            setOptional(statement, 16, event.resultingCustodyState());
            setOptional(statement, 17, event.reason());
            setOptional(statement, 18, event.comment());
            setOptional(statement, 19, event.correctionText());
            setOptional(statement, 20, event.correctedEventId());
            setOptional(statement, 21, event.correctedNoteId());
            statement.executeUpdate();
            return eventId;
        } catch (SQLException exception) {
            throw new ServiceException.StorageFailure(
                    "The audit event could not be recorded", exception);
        }
    }

    private static void setOptional(
            PreparedStatement statement, int index, Optional<?> value) throws SQLException {
        statement.setString(index, value.map(Object::toString).orElse(null));
    }
}
