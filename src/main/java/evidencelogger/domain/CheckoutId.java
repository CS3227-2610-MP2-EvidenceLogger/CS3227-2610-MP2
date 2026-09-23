package evidencelogger.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for an acknowledged checkout. */
public record CheckoutId(UUID value) {
    public CheckoutId {
        Objects.requireNonNull(value, "value");
    }

    public static CheckoutId parse(String value) {
        return new CheckoutId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
