package evidencelogger.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.UserId;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.ExaminationNoteRecord;
import evidencelogger.repository.checkout.ExaminationNoteRepository;
import evidencelogger.repository.checkout.NoteCorrectionRecord;

/** SQLite/JDBC implementation of append-only examination-note persistence. */
public final class JdbcExaminationNoteRepository implements ExaminationNoteRepository {
    private static final String NOTE_COLUMNS = "note_id, checkout_id, author_id, text, created_at";
    private static final String CORRECTION_COLUMNS = "note_id, author_id, correction_text, "
            + "reason, created_at";

    @Override
    public Optional<ExaminationNoteRecord> findById(
            Connection connection, ExaminationNoteId noteId) {
        String sql = "SELECT " + NOTE_COLUMNS + " FROM examination_note WHERE note_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, noteId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapNote(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw storageFailure("find examination note", exception);
        }
    }

    @Override
    public List<ExaminationNoteRecord> findForCheckout(
            Connection connection, CheckoutId checkoutId) {
        String sql = "SELECT " + NOTE_COLUMNS + " FROM examination_note"
                + " WHERE checkout_id = ? ORDER BY created_at, note_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, checkoutId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ExaminationNoteRecord> notes = new ArrayList<>();
                while (resultSet.next()) {
                    notes.add(mapNote(resultSet));
                }
                return List.copyOf(notes);
            }
        } catch (SQLException exception) {
            throw storageFailure("list examination notes", exception);
        }
    }

    @Override
    public List<NoteCorrectionRecord> findCorrections(
            Connection connection, ExaminationNoteId noteId) {
        String sql = "SELECT " + CORRECTION_COLUMNS + " FROM note_correction"
                + " WHERE note_id = ? ORDER BY created_at, rowid";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, noteId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<NoteCorrectionRecord> corrections = new ArrayList<>();
                while (resultSet.next()) {
                    corrections.add(mapCorrection(resultSet));
                }
                return List.copyOf(corrections);
            }
        } catch (SQLException exception) {
            throw storageFailure("list note corrections", exception);
        }
    }

    @Override
    public void insert(Connection connection, ExaminationNoteRecord note) {
        String sql = "INSERT INTO examination_note (" + NOTE_COLUMNS + ") VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, note.noteId().toString());
            statement.setString(2, note.checkoutId().toString());
            statement.setString(3, note.authorId().toString());
            statement.setString(4, note.text());
            statement.setString(5, note.createdAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if (isConstraintViolation(exception)) {
                throw new RepositoryException.Conflict(
                        "examination note conflicts with an existing record");
            }
            throw storageFailure("insert examination note", exception);
        }
    }

    @Override
    public void appendCorrection(Connection connection, NoteCorrectionRecord correction) {
        String sql = "INSERT INTO note_correction (" + CORRECTION_COLUMNS
                + ") VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, correction.noteId().toString());
            statement.setString(2, correction.authorId().toString());
            statement.setString(3, correction.correctionText());
            statement.setString(4, correction.reason());
            statement.setString(5, correction.createdAt().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if (isConstraintViolation(exception)) {
                throw new RepositoryException.Conflict(
                        "note correction conflicts with an existing record");
            }
            throw storageFailure("append note correction", exception);
        }
    }

    private static ExaminationNoteRecord mapNote(ResultSet resultSet) throws SQLException {
        return new ExaminationNoteRecord(
                ExaminationNoteId.parse(resultSet.getString("note_id")),
                CheckoutId.parse(resultSet.getString("checkout_id")),
                UserId.parse(resultSet.getString("author_id")),
                resultSet.getString("text"),
                Instant.parse(resultSet.getString("created_at")));
    }

    private static NoteCorrectionRecord mapCorrection(ResultSet resultSet) throws SQLException {
        return new NoteCorrectionRecord(
                ExaminationNoteId.parse(resultSet.getString("note_id")),
                UserId.parse(resultSet.getString("author_id")),
                resultSet.getString("correction_text"),
                resultSet.getString("reason"),
                Instant.parse(resultSet.getString("created_at")));
    }

    private static boolean isConstraintViolation(SQLException exception) {
        return "23000".equals(exception.getSQLState())
                || String.valueOf(exception.getMessage()).toLowerCase().contains("constraint");
    }

    private static RepositoryException.StorageFailure storageFailure(
            String operation, SQLException exception) {
        return new RepositoryException.StorageFailure("Unable to " + operation, exception);
    }
}
