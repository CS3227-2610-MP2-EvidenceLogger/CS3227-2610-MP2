package evidencelogger.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.history.AuditEventReadRepository;

/** JDBC implementation of assignment-scoped audit-event history reads. */
public final class JdbcAuditEventReadRepository implements AuditEventReadRepository {
    @Override
    public List<EventDetails> listEventsForCase(
            Connection connection, CaseId caseId, Optional<UserId> investigatorScope) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(investigatorScope, "investigatorScope");
        String sql = "SELECT event.id, event.event_type, event.actor_id, "
                + "actor.display_name AS actor_display_name, event.actor_role, event.event_time, "
                + "event.evidence_id, evidence.public_reference AS evidence_reference, "
                + "event.request_id, event.handoff_id, event.checkout_id, "
                + "event.previous_request_status, event.resulting_request_status, "
                + "event.previous_custody_state, event.resulting_custody_state, "
                + "event.correction_text, event.reason, event.corrected_event_id "
                + "FROM audit_event event "
                + "JOIN user_account actor ON actor.id = event.actor_id"
                + " LEFT JOIN evidence_item evidence ON evidence.id = event.evidence_id"
                + scopeJoin(investigatorScope)
                + " WHERE event.case_id = ? ORDER BY event.event_time, event.id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = bindScope(statement, investigatorScope);
            statement.setString(parameter, caseId.toString());
            try (ResultSet results = statement.executeQuery()) {
                List<EventDetails> events = new ArrayList<>();
                while (results.next()) {
                    events.add(new EventDetails(
                            AuditEventId.parse(results.getString("id")),
                            AuditEventType.valueOf(results.getString("event_type")),
                            UserId.parse(results.getString("actor_id")),
                            results.getString("actor_display_name"),
                            Role.valueOf(results.getString("actor_role")),
                            Instant.parse(results.getString("event_time")),
                            optionalId(results, "evidence_id", EvidenceId::parse),
                            Optional.ofNullable(results.getString("evidence_reference")),
                            optionalId(results, "request_id", CheckoutRequestId::parse),
                            optionalId(results, "handoff_id", HandoffId::parse),
                            optionalId(results, "checkout_id", CheckoutId::parse),
                            optionalEnum(results, "previous_request_status",
                                    CheckoutRequestStatus.class),
                            optionalEnum(results, "resulting_request_status",
                                    CheckoutRequestStatus.class),
                            optionalEnum(results, "previous_custody_state",
                                    EvidenceCustodyState.class),
                            optionalEnum(results, "resulting_custody_state",
                                    EvidenceCustodyState.class),
                            Optional.ofNullable(results.getString("correction_text")),
                            Optional.ofNullable(results.getString("reason")),
                            optionalId(results, "corrected_event_id", AuditEventId::parse)));
                }
                return List.copyOf(events);
            }
        } catch (SQLException exception) {
            throw new RepositoryException.StorageFailure("Unable to read audit history", exception);
        }
    }

    @Override
    public Optional<EventSubjects> findEventSubjects(
            Connection connection, AuditEventId eventId) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(eventId, "eventId");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT case_id, evidence_id, request_id, handoff_id, checkout_id
                FROM audit_event WHERE id = ?
                """)) {
            statement.setString(1, eventId.toString());
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return Optional.empty();
                }
                return Optional.of(new EventSubjects(
                        optionalId(results, "case_id", CaseId::parse),
                        optionalId(results, "evidence_id", EvidenceId::parse),
                        optionalId(results, "request_id", CheckoutRequestId::parse),
                        optionalId(results, "handoff_id", HandoffId::parse),
                        optionalId(results, "checkout_id", CheckoutId::parse)));
            }
        } catch (SQLException exception) {
            throw new RepositoryException.StorageFailure(
                    "Unable to read the audit event", exception);
        }
    }

    private static <T> Optional<T> optionalId(
            ResultSet results,
            String column,
            java.util.function.Function<String, T> parser) throws SQLException {
        return Optional.ofNullable(results.getString(column)).map(parser);
    }

    private static <T extends Enum<T>> Optional<T> optionalEnum(
            ResultSet results, String column, Class<T> enumType) throws SQLException {
        return Optional.ofNullable(results.getString(column))
                .map(value -> Enum.valueOf(enumType, value));
    }

    private static String scopeJoin(Optional<UserId> investigatorScope) {
        return investigatorScope.isPresent()
                ? " JOIN case_assignment assignment ON assignment.case_id = event.case_id"
                        + " AND assignment.investigator_id = ?"
                : "";
    }

    private static int bindScope(PreparedStatement statement, Optional<UserId> investigatorScope)
            throws SQLException {
        if (investigatorScope.isPresent()) {
            statement.setString(1, investigatorScope.orElseThrow().toString());
            return 2;
        }
        return 1;
    }
}
