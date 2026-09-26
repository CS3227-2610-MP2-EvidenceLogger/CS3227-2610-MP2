package evidencelogger.ui.common;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import evidencelogger.service.dto.HistoryViews;

/** Formats the shared authorized history model for both role views. */
public final class HistoryEventFormatter {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm z")
            .withZone(ZoneId.systemDefault());

    private HistoryEventFormatter() {
    }

    /** Returns one readable row with subjects, transitions, and correction details. */
    public static String format(HistoryViews.Event event) {
        Objects.requireNonNull(event, "event");
        List<String> details = new ArrayList<>();
        event.evidenceReference().ifPresent(reference -> details.add("evidence " + reference));
        event.requestId().ifPresent(id -> details.add("request " + id));
        event.handoffId().ifPresent(id -> details.add("handoff " + id));
        event.checkoutId().ifPresent(id -> details.add("checkout " + id));
        addTransition(details, "request",
                event.previousRequestStatus(), event.resultingRequestStatus());
        addTransition(details, "custody",
                event.previousCustodyState(), event.resultingCustodyState());
        event.correctionText().ifPresent(text -> details.add("correction: " + text));
        event.reason().ifPresent(reason -> details.add("reason: " + reason));
        event.correctedEventId().ifPresent(id -> details.add("corrects event " + id));
        String suffix = details.isEmpty() ? "" : " | " + String.join(" | ", details);
        return String.format("%s | %s | %s (%s)%s",
                TIME_FORMAT.format(event.eventTime()), event.type(),
                event.actorDisplayName(), event.actorRole(), suffix);
    }

    /** Adds an explicitly labelled transition when either endpoint is present. */
    private static void addTransition(
            List<String> details,
            String label,
            Optional<?> previous,
            Optional<?> resulting) {
        if (previous.isPresent() || resulting.isPresent()) {
            details.add(label + " " + previous.map(Object::toString).orElse("—")
                    + " → " + resulting.map(Object::toString).orElse("—"));
        }
    }
}
