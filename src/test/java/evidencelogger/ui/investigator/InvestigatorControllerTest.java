package evidencelogger.ui.investigator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.EvidenceId;
import evidencelogger.service.ServiceException;
import evidencelogger.service.checkout.CheckoutCommandService;
import evidencelogger.service.dto.CaseworkViews;

class InvestigatorControllerTest {
    @Test
    void validatesRequestFormBeforeSendingOnlyBusinessFieldsToTheCommandService() {
        RecordingCommands commands = new RecordingCommands();
        InvestigatorController controller = InvestigatorController.forCommands(commands);
        CaseworkViews.Evidence evidence = InvestigatorFixtures.evidence();

        InvestigatorController.Result<?> invalid = controller.submitRequest(
                evidence, " ", "not-a-timestamp");
        InvestigatorController.Result<?> submitted = controller.submitRequest(
                evidence, "Review seal", "26/09/2026 17:00");

        assertFalse(invalid.successful());
        assertEquals("Purpose is required", invalid.message());
        assertTrue(submitted.successful());
        assertEquals(evidence.evidenceId(), commands.evidenceId);
        assertEquals("Review seal", commands.purpose);
        assertEquals(Instant.parse("2026-09-26T17:00:00Z"), commands.expectedReturnAt);
    }

    @Test
    void rejectsMissingExpectedReturnWithoutCallingTheCommandService() {
        RecordingCommands commands = new RecordingCommands();
        InvestigatorController controller = InvestigatorController.forCommands(commands);

        InvestigatorController.Result<?> result = controller.submitRequest(
                InvestigatorFixtures.evidence(), "Review seal", null);

        assertFalse(result.successful());
        assertEquals("Expected return date and time is required", result.message());
        assertNull(commands.evidenceId);
    }

    @Test
    void rejectsAnInvalidExpectedReturnDateOrTimeWithoutCallingTheCommandService() {
        RecordingCommands commands = new RecordingCommands();
        InvestigatorController controller = InvestigatorController.forCommands(commands);

        InvestigatorController.Result<?> invalidDate = controller.submitRequest(
                InvestigatorFixtures.evidence(), "Review seal", "31/02/2026 17:00");
        InvestigatorController.Result<?> invalidTime = controller.submitRequest(
                InvestigatorFixtures.evidence(), "Review seal", "26/09/2026 24:00");

        assertFalse(invalidDate.successful());
        assertFalse(invalidTime.successful());
        assertEquals("Expected return date and time must use DD/MM/YYYY HH:MM",
                invalidDate.message());
        assertEquals("Expected return date and time must use DD/MM/YYYY HH:MM",
                invalidTime.message());
        assertNull(commands.evidenceId);
    }

    @Test
    void validatesWithdrawalSelectionAndSendsOnlyTheSelectedRequest() {
        RecordingCommands commands = new RecordingCommands();
        InvestigatorController controller = InvestigatorController.forCommands(commands);
        CheckoutRequestId requestId = new CheckoutRequestId(UUID.randomUUID());

        InvestigatorController.Result<?> missing = controller.withdrawRequest(null);
        InvestigatorController.Result<?> withdrawn = controller.withdrawRequest(requestId);

        assertFalse(missing.successful());
        assertEquals("Select a pending request", missing.message());
        assertTrue(withdrawn.successful());
        assertEquals(requestId, commands.requestId);
    }

    @Test
    void mapsTypedServiceFailuresToSafePresentationMessages() {
        CheckoutCommandService commands = new RecordingCommands() {
            @Override
            public void withdrawRequest(
                    evidencelogger.service.dto.CheckoutCommands.WithdrawRequest command) {
                throw new evidencelogger.service.ServiceException.Forbidden(
                        "The request is no longer assigned to you");
            }
        };
        InvestigatorController controller = InvestigatorController.forCommands(commands);

        InvestigatorController.Result<?> result = controller.withdrawRequest(
                new CheckoutRequestId(UUID.randomUUID()));

        assertFalse(result.successful());
        assertEquals("The request is no longer assigned to you", result.message());
    }

    @Test
    void storageFailureIncludesDiagnosticReference() {
        CheckoutCommandService commands = new RecordingCommands() {
            @Override
            public void withdrawRequest(
                    evidencelogger.service.dto.CheckoutCommands.WithdrawRequest command) {
                throw new ServiceException.StorageFailure(
                        "Checkout data could not be read",
                        new IllegalStateException("database unavailable"));
            }
        };
        InvestigatorController controller = InvestigatorController.forCommands(commands);

        InvestigatorController.Result<?> result = controller.withdrawRequest(
                new CheckoutRequestId(UUID.randomUUID()));

        assertFalse(result.successful());
        assertTrue(result.message().startsWith(
                "Checkout data could not be read. Reference: "));
    }

    private static class RecordingCommands implements CheckoutCommandService {
        private EvidenceId evidenceId;
        private String purpose;
        private Instant expectedReturnAt;
        private CheckoutRequestId requestId;

        @Override
        public evidencelogger.domain.CheckoutRequestId submitRequest(
                evidencelogger.service.dto.CheckoutCommands.SubmitRequest command) {
            evidenceId = command.evidenceId();
            purpose = command.purpose();
            expectedReturnAt = command.expectedReturnAt();
            return new evidencelogger.domain.CheckoutRequestId(UUID.randomUUID());
        }

        @Override
        public void withdrawRequest(
                evidencelogger.service.dto.CheckoutCommands.WithdrawRequest command) {
            requestId = command.requestId();
        }

        @Override
        public void approveRequest(
                evidencelogger.service.dto.CheckoutCommands.ApproveRequest command) {
        }

        @Override
        public void rejectRequest(
                evidencelogger.service.dto.CheckoutCommands.RejectRequest command) {
        }

        @Override
        public void cancelApprovedRequest(
                evidencelogger.service.dto.CheckoutCommands.CancelApprovedRequest command) {
        }

        @Override
        public evidencelogger.domain.HandoffId recordHandoff(
                evidencelogger.service.dto.CheckoutCommands.RecordHandoff command) {
            return null;
        }

        @Override
        public void reverseHandoff(
                evidencelogger.service.dto.CheckoutCommands.ReverseHandoff command) {
        }

        @Override
        public evidencelogger.domain.CheckoutId acknowledgeCollection(
                evidencelogger.service.dto.CheckoutCommands.AcknowledgeCollection command) {
            return null;
        }

        @Override
        public evidencelogger.domain.ExaminationNoteId addExaminationNote(
                evidencelogger.service.dto.CheckoutCommands.AddExaminationNote command) {
            return null;
        }

        @Override
        public void correctExaminationNote(
                evidencelogger.service.dto.CheckoutCommands.CorrectExaminationNote command) {
        }

        @Override
        public void initiateReturn(
                evidencelogger.service.dto.CheckoutCommands.InitiateReturn command) {
        }

        @Override
        public void inspectReturn(
                evidencelogger.service.dto.CheckoutCommands.InspectReturn command) {
        }

        @Override
        public void inspectUnplannedReturn(
                evidencelogger.service.dto.CheckoutCommands.InspectUnplannedReturn command) {
        }
    }

    private static final class InvestigatorFixtures {
        private static CaseworkViews.Evidence evidence() {
            return new CaseworkViews.Evidence(
                    new EvidenceId(UUID.randomUUID()),
                    new evidencelogger.domain.CaseId(UUID.randomUUID()),
                    "Case One", "EV-001", "Sealed bag",
                    new evidencelogger.domain.StorageLocationId(UUID.randomUUID()),
                    "Locker A", evidencelogger.domain.EvidenceCustodyState.IN_STORAGE,
                    Instant.parse("2026-09-25T08:00:00Z"));
        }
    }
}
