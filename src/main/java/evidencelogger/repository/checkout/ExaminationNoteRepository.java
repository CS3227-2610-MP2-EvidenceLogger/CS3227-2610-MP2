package evidencelogger.repository.checkout;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.ExaminationNoteId;

/** Persistence operations for append-only examination notes and corrections. */
public interface ExaminationNoteRepository {
    Optional<ExaminationNoteRecord> findById(Connection connection, ExaminationNoteId noteId);

    List<ExaminationNoteRecord> findForCheckout(Connection connection, CheckoutId checkoutId);

    List<NoteCorrectionRecord> findCorrections(
            Connection connection, ExaminationNoteId noteId);

    void insert(Connection connection, ExaminationNoteRecord note);

    void appendCorrection(Connection connection, NoteCorrectionRecord correction);
}
