package evidencelogger.service.checkout;

import java.time.Clock;
import java.util.Objects;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.infrastructure.time.IdGenerator;
import evidencelogger.repository.EvidenceRepository;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutRepository;
import evidencelogger.repository.checkout.CheckoutRequestRepository;
import evidencelogger.repository.checkout.ExaminationNoteRepository;
import evidencelogger.repository.checkout.HandoffRepository;
import evidencelogger.repository.checkout.ReturnInspectionRepository;
import evidencelogger.service.auth.AuthorizationService;
import evidencelogger.service.dto.CheckoutCommands;
import evidencelogger.service.history.AuditEventWriter;

/** Transactional checkout commands for request and decision workflow actions. */
public final class DefaultCheckoutCommandService implements CheckoutCommandService {
    private final TransactionRunner transactions;
    private final CheckoutRequestWorkflow requestWorkflow;
    private final CheckoutCustodyWorkflow custodyWorkflow;
    private final CheckoutExaminationWorkflow examinationWorkflow;
    private final CheckoutReturnWorkflow returnWorkflow;

    /** Creates checkout commands with all application collaborators injected. */
    public DefaultCheckoutCommandService(
            TransactionRunner transactions,
            AuthorizationService authorization,
            EvidenceRepository evidenceRepository,
            CheckoutRequestRepository requestRepository,
            HandoffRepository handoffRepository,
            CheckoutRepository checkoutRepository,
            ExaminationNoteRepository noteRepository,
            ReturnInspectionRepository inspectionRepository,
            AuditEventWriter auditEvents,
            IdGenerator<CheckoutRequestId> requestIds,
            IdGenerator<HandoffId> handoffIds,
            IdGenerator<CheckoutId> checkoutIds,
            IdGenerator<ExaminationNoteId> noteIds,
            Clock clock) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.requestWorkflow = new CheckoutRequestWorkflow(
                authorization,
                evidenceRepository,
                requestRepository,
                auditEvents,
                requestIds,
                clock);
        this.custodyWorkflow = new CheckoutCustodyWorkflow(
                authorization,
                evidenceRepository,
                requestRepository,
                handoffRepository,
                checkoutRepository,
                auditEvents,
                handoffIds,
                checkoutIds,
                clock);
        this.examinationWorkflow = new CheckoutExaminationWorkflow(
                authorization,
                evidenceRepository,
                checkoutRepository,
                noteRepository,
                auditEvents,
                noteIds,
                clock);
        this.returnWorkflow = new CheckoutReturnWorkflow(
                authorization,
                evidenceRepository,
                checkoutRepository,
                inspectionRepository,
                auditEvents,
                clock);
    }

    @Override
    public CheckoutRequestId submitRequest(CheckoutCommands.SubmitRequest command) {
        Objects.requireNonNull(command, "command");
        return inTransaction(
                connection -> requestWorkflow.submit(connection, command));
    }

    @Override
    public void withdrawRequest(CheckoutCommands.WithdrawRequest command) {
        Objects.requireNonNull(command, "command");
        inTransaction(connection -> {
            requestWorkflow.withdraw(connection, command);
            return null;
        });
    }

    @Override
    public void approveRequest(CheckoutCommands.ApproveRequest command) {
        Objects.requireNonNull(command, "command");
        inTransaction(connection -> {
            requestWorkflow.approve(connection, command);
            return null;
        });
    }

    @Override
    public void rejectRequest(CheckoutCommands.RejectRequest command) {
        Objects.requireNonNull(command, "command");
        inTransaction(connection -> {
            requestWorkflow.reject(connection, command);
            return null;
        });
    }

    @Override
    public void cancelApprovedRequest(CheckoutCommands.CancelApprovedRequest command) {
        Objects.requireNonNull(command, "command");
        inTransaction(connection -> {
            requestWorkflow.cancel(connection, command);
            return null;
        });
    }

    @Override
    public HandoffId recordHandoff(CheckoutCommands.RecordHandoff command) {
        Objects.requireNonNull(command, "command");
        return inTransaction(
                connection -> custodyWorkflow.recordHandoff(connection, command));
    }

    @Override
    public void reverseHandoff(CheckoutCommands.ReverseHandoff command) {
        Objects.requireNonNull(command, "command");
        inTransaction(connection -> {
            custodyWorkflow.reverseHandoff(connection, command);
            return null;
        });
    }

    @Override
    public CheckoutId acknowledgeCollection(
            CheckoutCommands.AcknowledgeCollection command) {
        Objects.requireNonNull(command, "command");
        return inTransaction(
                connection -> custodyWorkflow.acknowledgeCollection(connection, command));
    }

    @Override
    public ExaminationNoteId addExaminationNote(
            CheckoutCommands.AddExaminationNote command) {
        Objects.requireNonNull(command, "command");
        return inTransaction(
                connection -> examinationWorkflow.addNote(connection, command));
    }

    @Override
    public void correctExaminationNote(CheckoutCommands.CorrectExaminationNote command) {
        Objects.requireNonNull(command, "command");
        inTransaction(connection -> {
            examinationWorkflow.correctNote(connection, command);
            return null;
        });
    }

    @Override
    public void initiateReturn(CheckoutCommands.InitiateReturn command) {
        Objects.requireNonNull(command, "command");
        inTransaction(connection -> {
            returnWorkflow.initiate(connection, command);
            return null;
        });
    }

    @Override
    public void inspectReturn(CheckoutCommands.InspectReturn command) {
        Objects.requireNonNull(command, "command");
        inTransaction(connection -> {
            returnWorkflow.inspect(connection, command);
            return null;
        });
    }

    @Override
    public void inspectUnplannedReturn(CheckoutCommands.InspectUnplannedReturn command) {
        Objects.requireNonNull(command, "command");
        inTransaction(connection -> {
            returnWorkflow.inspectUnplanned(connection, command);
            return null;
        });
    }

    private <T> T inTransaction(TransactionRunner.TransactionalWork<T> work) {
        try {
            return transactions.inTransaction(work);
        } catch (RepositoryException exception) {
            throw CheckoutRepositoryErrors.translate(exception);
        }
    }
}
