package evidencelogger.repository.checkout;

import java.time.Instant;
import java.util.Objects;

import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.UserId;

/** Immutable persisted append-only examination-note correction. */
public record NoteCorrectionRecord(
        ExaminationNoteId noteId,
        UserId authorId,
        String correctionText,
        String reason,
        Instant createdAt) {
    /** Validates the persisted note-correction fields. */
    public NoteCorrectionRecord {
        Objects.requireNonNull(noteId, "noteId");
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(correctionText, "correctionText");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
