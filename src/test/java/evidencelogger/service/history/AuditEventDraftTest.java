package evidencelogger.service.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.domain.UserId;

class AuditEventDraftTest {
    @Test
    void assignmentAndLocationSubjectsRemainTypedAndDistinct() {
        CaseId caseId = new CaseId(UUID.randomUUID());
        UserId investigatorId = new UserId(UUID.randomUUID());
        StorageLocationId locationId = new StorageLocationId(UUID.randomUUID());

        AuditEventDraft draft = draft(
                AuditEventType.CASE_ASSIGNED,
                Optional.of(caseId),
                Optional.of(investigatorId),
                Optional.of(locationId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        assertEquals(Optional.of(caseId), draft.caseId());
        assertEquals(Optional.of(investigatorId), draft.assignedInvestigatorId());
        assertEquals(Optional.of(locationId), draft.storageLocationId());
    }

    @Test
    void unplannedStoredReturnPreservesReason() {
        AuditEventDraft draft = draft(
                AuditEventType.UNPLANNED_RETURN_INSPECTED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(EvidenceCustodyState.IN_STORAGE),
                Optional.of("Return was not initiated"),
                Optional.empty(),
                Optional.empty());

        assertEquals(Optional.of("Return was not initiated"), draft.reason());
    }

    @Test
    void historyCorrectionPreservesReasonAndCorrectionTextSeparately() {
        AuditEventDraft draft = draft(
                AuditEventType.HISTORY_CORRECTED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of("Original description was inaccurate"),
                Optional.empty(),
                Optional.of("Corrected documentary description"));

        assertEquals(Optional.of("Original description was inaccurate"), draft.reason());
        assertEquals(Optional.of("Corrected documentary description"), draft.correctionText());
    }

    private static AuditEventDraft draft(
            AuditEventType type,
            Optional<CaseId> caseId,
            Optional<UserId> assignedInvestigatorId,
            Optional<StorageLocationId> storageLocationId,
            Optional<EvidenceCustodyState> resultingCustodyState,
            Optional<String> reason,
            Optional<String> comment,
            Optional<String> correctionText) {
        return new AuditEventDraft(
                type,
                caseId,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                assignedInvestigatorId,
                storageLocationId,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                resultingCustodyState,
                reason,
                comment,
                correctionText,
                Optional.empty(),
                Optional.empty());
    }
}
