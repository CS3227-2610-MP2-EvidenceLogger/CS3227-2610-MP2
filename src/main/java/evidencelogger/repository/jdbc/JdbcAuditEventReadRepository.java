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
                + "event.correction_text, event.reason FROM audit_event event "
                + "JOIN user_account actor ON actor.id = event.actor_id"
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
                            Optional.ofNullable(results.getString("correction_text")),
                            Optional.ofNullable(results.getString("reason"))));
                }
                return List.copyOf(events);
            }
        } catch (SQLException exception) {
            throw new RepositoryException.StorageFailure("Unable to read audit history", exception);
        }
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
