package evidencelogger.repository.checkout;

import java.time.Instant;
import java.util.Objects;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.UserId;

/** Immutable persisted original examination note. */
public record ExaminationNoteRecord(
        ExaminationNoteId noteId,
        CheckoutId checkoutId,
        UserId authorId,
        String text,
        Instant createdAt) {
    /** Validates the persisted examination-note fields. */
    public ExaminationNoteRecord {
        Objects.requireNonNull(noteId, "noteId");
        Objects.requireNonNull(checkoutId, "checkoutId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
