package evidencelogger.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.domain.UserId;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutReadRepository;

/** JDBC implementation of scoped, display-ready checkout reads. */
public final class JdbcCheckoutReadRepository implements CheckoutReadRepository {
    private static final String REQUEST_COLUMNS = "r.id AS request_id, r.evidence_id, "
            + "e.public_reference, e.case_id, c.title AS case_title, r.requester_id, "
            + "requester.display_name AS requester_display_name, r.purpose, "
            + "r.expected_return_at, r.status, e.custody_state AS evidence_state, "
            + "r.requested_at AS submitted_at, h.id AS handoff_id, co.id AS checkout_id";
    private static final String REQUEST_TABLES = " FROM checkout_request r"
            + " JOIN evidence_item e ON e.id = r.evidence_id"
            + " JOIN case_record c ON c.id = e.case_id"
            + " JOIN user_account requester ON requester.id = r.requester_id"
            + " LEFT JOIN handoff h ON h.request_id = r.id"
            + " LEFT JOIN checkout co ON co.request_id = r.id";
    private static final String CHECKOUT_COLUMNS = "co.id AS checkout_id, co.request_id, "
            + "co.evidence_id, e.public_reference, e.case_id, c.title AS case_title, "
            + "co.collector_id, collector.display_name AS collector_display_name, "
            + "co.collected_at, co.return_initiated_at, co.completed_at, "
            + "e.custody_state AS evidence_state";
    private static final String CHECKOUT_TABLES = " FROM checkout co"
            + " JOIN evidence_item e ON e.id = co.evidence_id"
            + " JOIN case_record c ON c.id = e.case_id"
            + " JOIN user_account collector ON collector.id = co.collector_id";
    private static final String NOTE_COLUMNS = "n.id AS note_id, n.checkout_id, n.author_id, "
            + "author.display_name AS author_display_name, n.note_text, n.created_at";
    private static final String NOTE_TABLES = " FROM examination_note n"
            + " JOIN checkout co ON co.id = n.checkout_id"
            + " JOIN evidence_item e ON e.id = co.evidence_id"
            + " JOIN case_record c ON c.id = e.case_id"
            + " JOIN user_account author ON author.id = n.author_id";

    @Override
    public List<RequestDetails> listRequests(
            Connection connection,
            Optional<CheckoutRequestStatus> status,
            Optional<UserId> investigatorScope) {
        requireScope(status, investigatorScope);
        String sql = "SELECT " + REQUEST_COLUMNS + REQUEST_TABLES + scopedTables(investigatorScope)
                + (status.isPresent() ? " WHERE r.status = ?" : "")
                + " ORDER BY r.requested_at, r.id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = bindScope(statement, investigatorScope);
            if (status.isPresent()) {
                statement.setString(parameter, status.orElseThrow().name());
            }
            return mapRequests(statement);
        } catch (SQLException exception) {
            throw storageFailure("list checkout requests", exception);
        }
    }

    @Override
    public List<RequestDetails> listRequestsForCase(
            Connection connection, CaseId caseId, Optional<UserId> investigatorScope) {
        requireScope(caseId, investigatorScope);
        String sql = "SELECT " + REQUEST_COLUMNS + REQUEST_TABLES + scopedTables(investigatorScope)
                + " WHERE e.case_id = ? ORDER BY r.requested_at, r.id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = bindScope(statement, investigatorScope);
            statement.setString(parameter, caseId.toString());
            return mapRequests(statement);
        } catch (SQLException exception) {
            throw storageFailure("list checkout requests for case", exception);
        }
    }

    @Override
    public Optional<RequestDetails> findRequest(
            Connection connection,
            CheckoutRequestId requestId,
            Optional<UserId> investigatorScope) {
        requireScope(requestId, investigatorScope);
        String sql = "SELECT " + REQUEST_COLUMNS + REQUEST_TABLES + scopedTables(investigatorScope)
                + " WHERE r.id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = bindScope(statement, investigatorScope);
            statement.setString(parameter, requestId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapRequest(results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw storageFailure("find checkout request", exception);
        }
    }

    @Override
    public List<CheckoutDetails> listCheckouts(
            Connection connection, Optional<UserId> investigatorScope) {
        requireScope(investigatorScope);
        String sql = "SELECT " + CHECKOUT_COLUMNS + CHECKOUT_TABLES + scopedTables(investigatorScope)
                + " ORDER BY co.collected_at, co.id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindScope(statement, investigatorScope);
            return mapCheckouts(statement);
        } catch (SQLException exception) {
            throw storageFailure("list checkouts", exception);
        }
    }

    @Override
    public List<CheckoutDetails> listCheckoutsForCase(
            Connection connection, CaseId caseId, Optional<UserId> investigatorScope) {
        requireScope(caseId, investigatorScope);
        String sql = "SELECT " + CHECKOUT_COLUMNS + CHECKOUT_TABLES + scopedTables(investigatorScope)
                + " WHERE e.case_id = ? ORDER BY co.collected_at, co.id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = bindScope(statement, investigatorScope);
            statement.setString(parameter, caseId.toString());
            return mapCheckouts(statement);
        } catch (SQLException exception) {
            throw storageFailure("list checkouts for case", exception);
        }
    }

    @Override
    public Optional<CheckoutDetails> findCheckout(
            Connection connection, CheckoutId checkoutId, Optional<UserId> investigatorScope) {
        requireScope(checkoutId, investigatorScope);
        String sql = "SELECT " + CHECKOUT_COLUMNS + CHECKOUT_TABLES + scopedTables(investigatorScope)
                + " WHERE co.id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = bindScope(statement, investigatorScope);
            statement.setString(parameter, checkoutId.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapCheckout(results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw storageFailure("find checkout", exception);
        }
    }

    @Override
    public List<ExaminationNoteDetails> listNotes(
            Connection connection, CheckoutId checkoutId, Optional<UserId> investigatorScope) {
        requireScope(checkoutId, investigatorScope);
        String sql = "SELECT " + NOTE_COLUMNS + NOTE_TABLES + scopedTables(investigatorScope)
                + " WHERE n.checkout_id = ? ORDER BY n.created_at, n.id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = bindScope(statement, investigatorScope);
            statement.setString(parameter, checkoutId.toString());
            try (ResultSet results = statement.executeQuery()) {
                List<ExaminationNoteDetails> notes = new ArrayList<>();
                while (results.next()) {
                    notes.add(mapNote(connection, results));
                }
                return List.copyOf(notes);
            }
        } catch (SQLException exception) {
            throw storageFailure("list examination notes", exception);
        }
    }

    private static List<RequestDetails> mapRequests(PreparedStatement statement) throws SQLException {
        try (ResultSet results = statement.executeQuery()) {
            List<RequestDetails> requests = new ArrayList<>();
            while (results.next()) {
                requests.add(mapRequest(results));
            }
            return List.copyOf(requests);
        }
    }

    private static List<CheckoutDetails> mapCheckouts(PreparedStatement statement) throws SQLException {
        try (ResultSet results = statement.executeQuery()) {
            List<CheckoutDetails> checkouts = new ArrayList<>();
            while (results.next()) {
                checkouts.add(mapCheckout(results));
            }
            return List.copyOf(checkouts);
        }
    }

    private static RequestDetails mapRequest(ResultSet results) throws SQLException {
        return new RequestDetails(
                CheckoutRequestId.parse(results.getString("request_id")),
                evidencelogger.domain.EvidenceId.parse(results.getString("evidence_id")),
                results.getString("public_reference"),
                CaseId.parse(results.getString("case_id")),
                results.getString("case_title"),
                UserId.parse(results.getString("requester_id")),
                results.getString("requester_display_name"),
                results.getString("purpose"),
                Instant.parse(results.getString("expected_return_at")),
                CheckoutRequestStatus.valueOf(results.getString("status")),
                EvidenceCustodyState.valueOf(results.getString("evidence_state")),
                Instant.parse(results.getString("submitted_at")),
                optionalHandoffId(results, "handoff_id"),
                optionalCheckoutId(results, "checkout_id"));
    }

    private static CheckoutDetails mapCheckout(ResultSet results) throws SQLException {
        return new CheckoutDetails(
                CheckoutId.parse(results.getString("checkout_id")),
                CheckoutRequestId.parse(results.getString("request_id")),
                evidencelogger.domain.EvidenceId.parse(results.getString("evidence_id")),
                results.getString("public_reference"),
                CaseId.parse(results.getString("case_id")),
                results.getString("case_title"),
                UserId.parse(results.getString("collector_id")),
                results.getString("collector_display_name"),
                Instant.parse(results.getString("collected_at")),
                optionalInstant(results, "return_initiated_at"),
                optionalInstant(results, "completed_at"),
                EvidenceCustodyState.valueOf(results.getString("evidence_state")));
    }

    private static ExaminationNoteDetails mapNote(Connection connection, ResultSet results)
            throws SQLException {
        ExaminationNoteId noteId = ExaminationNoteId.parse(results.getString("note_id"));
        return new ExaminationNoteDetails(
                noteId,
                CheckoutId.parse(results.getString("checkout_id")),
                UserId.parse(results.getString("author_id")),
                results.getString("author_display_name"),
                results.getString("note_text"),
                Instant.parse(results.getString("created_at")),
                findCorrections(connection, noteId));
    }

    private static List<NoteCorrectionDetails> findCorrections(
            Connection connection, ExaminationNoteId noteId) throws SQLException {
        String sql = "SELECT correction.author_id, author.display_name AS author_display_name, "
                + "correction.correction_text, correction.reason, correction.created_at"
                + " FROM note_correction correction"
                + " JOIN user_account author ON author.id = correction.author_id"
                + " WHERE correction.note_id = ? ORDER BY correction.created_at, correction.id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, noteId.toString());
            try (ResultSet results = statement.executeQuery()) {
                List<NoteCorrectionDetails> corrections = new ArrayList<>();
                while (results.next()) {
                    corrections.add(new NoteCorrectionDetails(
                            UserId.parse(results.getString("author_id")),
                            results.getString("author_display_name"),
                            results.getString("correction_text"),
                            results.getString("reason"),
                            Instant.parse(results.getString("created_at"))));
                }
                return List.copyOf(corrections);
            }
        }
    }

    private static String scopedTables(Optional<UserId> investigatorScope) {
        return investigatorScope.isPresent()
                ? " JOIN case_assignment assignment ON assignment.case_id = e.case_id"
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

    private static Optional<HandoffId> optionalHandoffId(ResultSet results, String column)
            throws SQLException {
        String value = results.getString(column);
        return value == null ? Optional.empty() : Optional.of(HandoffId.parse(value));
    }

    private static Optional<CheckoutId> optionalCheckoutId(ResultSet results, String column)
            throws SQLException {
        String value = results.getString(column);
        return value == null ? Optional.empty() : Optional.of(CheckoutId.parse(value));
    }

    private static Optional<Instant> optionalInstant(ResultSet results, String column)
            throws SQLException {
        String value = results.getString(column);
        return value == null ? Optional.empty() : Optional.of(Instant.parse(value));
    }

    private static void requireScope(Object... values) {
        for (Object value : values) {
            java.util.Objects.requireNonNull(value, "query input");
        }
    }

    private static RepositoryException.StorageFailure storageFailure(
            String operation, SQLException exception) {
        return new RepositoryException.StorageFailure("Unable to " + operation, exception);
    }
}
