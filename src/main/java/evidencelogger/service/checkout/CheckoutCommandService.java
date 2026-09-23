package evidencelogger.service.checkout;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.service.dto.CheckoutCommands;

/** Authorized, transactional commands for the checkout lifecycle. */
public interface CheckoutCommandService {
    CheckoutRequestId submitRequest(CheckoutCommands.SubmitRequest command);

    void withdrawRequest(CheckoutCommands.WithdrawRequest command);

    void approveRequest(CheckoutCommands.ApproveRequest command);

    void rejectRequest(CheckoutCommands.RejectRequest command);

    void cancelApprovedRequest(CheckoutCommands.CancelApprovedRequest command);

    HandoffId recordHandoff(CheckoutCommands.RecordHandoff command);

    void reverseHandoff(CheckoutCommands.ReverseHandoff command);

    CheckoutId acknowledgeCollection(CheckoutCommands.AcknowledgeCollection command);

    ExaminationNoteId addExaminationNote(CheckoutCommands.AddExaminationNote command);

    void correctExaminationNote(CheckoutCommands.CorrectExaminationNote command);

    void initiateReturn(CheckoutCommands.InitiateReturn command);

    void inspectReturn(CheckoutCommands.InspectReturn command);

    void inspectUnplannedReturn(CheckoutCommands.InspectUnplannedReturn command);
}
