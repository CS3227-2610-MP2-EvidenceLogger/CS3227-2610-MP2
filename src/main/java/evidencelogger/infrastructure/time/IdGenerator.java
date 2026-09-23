package evidencelogger.infrastructure.time;

/** Injectable source of stable identifiers for deterministic service tests. */
@FunctionalInterface
public interface IdGenerator<T> {
    T nextId();
}
