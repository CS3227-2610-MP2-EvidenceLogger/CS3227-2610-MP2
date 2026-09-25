package evidencelogger.service.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import evidencelogger.domain.AuditEventType;
import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
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

    @Test
    void requestFactoryPreservesSubmissionWithNoPreviousStatus() {
        CaseId caseId = new CaseId(UUID.randomUUID());
        EvidenceId evidenceId = new EvidenceId(UUID.randomUUID());
        CheckoutRequestId requestId = new CheckoutRequestId(UUID.randomUUID());

        AuditEventDraft draft = AuditEventDraft.requestTransition(
                AuditEventType.REQUEST_SUBMITTED,
                caseId,
                evidenceId,
                requestId,
                Optional.empty(),
                Optional.of(CheckoutRequestStatus.PENDING),
                EvidenceCustodyState.IN_STORAGE,
                Optional.empty());

        assertEquals(Optional.of(caseId), draft.caseId());
        assertEquals(Optional.of(evidenceId), draft.evidenceId());
        assertEquals(Optional.of(requestId), draft.requestId());
        assertEquals(Optional.empty(), draft.previousRequestStatus());
        assertEquals(Optional.of(CheckoutRequestStatus.PENDING), draft.resultingRequestStatus());
        assertEquals(Optional.of(EvidenceCustodyState.IN_STORAGE), draft.previousCustodyState());
        assertEquals(Optional.of(EvidenceCustodyState.IN_STORAGE), draft.resultingCustodyState());
    }

    @Test
    void handoffFactoryPreservesRequestAndCustodyTransitions() {
        CheckoutRequestId requestId = new CheckoutRequestId(UUID.randomUUID());
        HandoffId handoffId = new HandoffId(UUID.randomUUID());
        AuditEventDraft draft = AuditEventDraft.handoffTransition(
                AuditEventType.HANDOFF_REVERSED,
                new CaseId(UUID.randomUUID()),
                new EvidenceId(UUID.randomUUID()),
                requestId,
                handoffId,
                CheckoutRequestStatus.APPROVED,
                CheckoutRequestStatus.CANCELLED,
                EvidenceCustodyState.HANDOFF_AWAITING_ACK,
                EvidenceCustodyState.IN_STORAGE,
                Optional.of("No longer required"));

        assertEquals(Optional.of(requestId), draft.requestId());
        assertEquals(Optional.of(handoffId), draft.handoffId());
        assertEquals(Optional.of(CheckoutRequestStatus.CANCELLED), draft.resultingRequestStatus());
        assertEquals(Optional.of(EvidenceCustodyState.IN_STORAGE), draft.resultingCustodyState());
        assertEquals(Optional.of("No longer required"), draft.reason());
    }

    @Test
    void collectionFactorySetsConsumedAndCheckedOutStates() {
        AuditEventDraft draft = AuditEventDraft.collectionAcknowledged(
                new CaseId(UUID.randomUUID()),
                new EvidenceId(UUID.randomUUID()),
                new CheckoutRequestId(UUID.randomUUID()),
                new HandoffId(UUID.randomUUID()),
                new CheckoutId(UUID.randomUUID()));

        assertEquals(AuditEventType.COLLECTION_ACKNOWLEDGED, draft.type());
        assertEquals(Optional.of(CheckoutRequestStatus.APPROVED), draft.previousRequestStatus());
        assertEquals(Optional.of(CheckoutRequestStatus.CONSUMED), draft.resultingRequestStatus());
        assertEquals(Optional.of(EvidenceCustodyState.HANDOFF_AWAITING_ACK),
                draft.previousCustodyState());
        assertEquals(Optional.of(EvidenceCustodyState.CHECKED_OUT), draft.resultingCustodyState());
    }

    @Test
    void examinationNoteFactorySeparatesCorrectionTargetAndText() {
        ExaminationNoteId noteId = new ExaminationNoteId(UUID.randomUUID());
        AuditEventDraft draft = AuditEventDraft.examinationNoteChange(
                AuditEventType.EXAMINATION_NOTE_CORRECTED,
                new CaseId(UUID.randomUUID()),
                new EvidenceId(UUID.randomUUID()),
                new CheckoutId(UUID.randomUUID()),
                Optional.empty(),
                Optional.of("Corrected note"),
                Optional.of("Typographical error"),
                Optional.of(noteId));

        assertEquals(Optional.of("Corrected note"), draft.correctionText());
        assertEquals(Optional.of("Typographical error"), draft.reason());
        assertEquals(Optional.of(noteId), draft.correctedNoteId());
    }

    @Test
    void custodyFactoryPreservesReturnStateAndReason() {
        AuditEventDraft draft = AuditEventDraft.custodyTransition(
                AuditEventType.UNPLANNED_RETURN_INSPECTED,
                new CaseId(UUID.randomUUID()),
                new EvidenceId(UUID.randomUUID()),
                new CheckoutId(UUID.randomUUID()),
                EvidenceCustodyState.CHECKED_OUT,
                EvidenceCustodyState.IN_STORAGE,
                Optional.of("Returned directly to storage"));

        assertEquals(Optional.of(EvidenceCustodyState.CHECKED_OUT), draft.previousCustodyState());
        assertEquals(Optional.of(EvidenceCustodyState.IN_STORAGE), draft.resultingCustodyState());
        assertEquals(Optional.of("Returned directly to storage"), draft.reason());
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
