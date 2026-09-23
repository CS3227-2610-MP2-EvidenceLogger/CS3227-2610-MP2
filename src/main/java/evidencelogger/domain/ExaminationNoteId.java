package evidencelogger.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for an original examination note. */
public record ExaminationNoteId(UUID value) {
    public ExaminationNoteId {
        Objects.requireNonNull(value, "value");
    }

    public static ExaminationNoteId parse(String value) {
        return new ExaminationNoteId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
