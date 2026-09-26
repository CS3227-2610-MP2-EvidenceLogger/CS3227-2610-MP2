package evidencelogger.ui.common;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import evidencelogger.domain.AuditEventId;
import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.Role;
import evidencelogger.service.dto.HistoryViews;

class HistoryEventFormatterTest {
    @Test
    void rendersSubjectTransitionCorrectionAndReasonTogether() {
        AuditEventId targetId = AuditEventId.parse(
                "00000000-0000-0000-0000-000000000901");
        HistoryViews.Event event = new HistoryViews.Event(
                AuditEventId.parse("00000000-0000-0000-0000-000000000902"),
                AuditEventType.HISTORY_CORRECTED,
                "Morgan Custodian",
                Role.EVIDENCE_CUSTODIAN,
                Instant.parse("2026-09-26T08:00:00Z"),
                Optional.of(EvidenceId.parse("00000000-0000-0000-0000-000000000903")),
                Optional.of("EV-001"),
                Optional.of(CheckoutRequestId.parse(
                        "00000000-0000-0000-0000-000000000904")),
                Optional.empty(),
                Optional.empty(),
                Optional.of(CheckoutRequestStatus.PENDING),
                Optional.of(CheckoutRequestStatus.REJECTED),
                Optional.of(EvidenceCustodyState.IN_STORAGE),
                Optional.of(EvidenceCustodyState.IN_STORAGE),
                Optional.of("Corrected documentary description"),
                Optional.of("Original wording was inaccurate"),
                Optional.of(targetId));

        String text = HistoryEventFormatter.format(event);

        assertTrue(text.contains("evidence EV-001"));
        assertTrue(text.contains("request PENDING → REJECTED"));
        assertTrue(text.contains("custody IN_STORAGE → IN_STORAGE"));
        assertTrue(text.contains("correction: Corrected documentary description"));
        assertTrue(text.contains("reason: Original wording was inaccurate"));
        assertTrue(text.contains("corrects event " + targetId));
    }
}
