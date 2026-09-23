package evidencelogger.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for a checkout request. */
public record CheckoutRequestId(UUID value) {
    public CheckoutRequestId {
        Objects.requireNonNull(value, "value");
    }

    public static CheckoutRequestId parse(String value) {
        return new CheckoutRequestId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
