package evidencelogger.ui.custodian;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.ReturnInspectionOutcome;
import evidencelogger.service.ServiceException;
import evidencelogger.service.checkout.CheckoutCommandService;
import evidencelogger.service.checkout.CheckoutQueryService;
import evidencelogger.service.dto.CheckoutCommands;
import evidencelogger.service.dto.CheckoutViews;
import evidencelogger.service.dto.HistoryViews;
import evidencelogger.service.history.HistoryQueryService;
import evidencelogger.ui.common.ServiceFailurePresenter;

/** Presentation logic for Custodian checkout workflow and history screens. */
public final class CustodianWorkflowController {
    private final CheckoutCommandService commands;
    private final CheckoutQueryService queries;
    private final HistoryQueryService history;

    /** Creates a controller backed by the shared authorized checkout interfaces. */
    public CustodianWorkflowController(
            CheckoutCommandService commands,
            CheckoutQueryService queries,
            HistoryQueryService history) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.history = Objects.requireNonNull(history, "history");
    }

    /** Lists every checkout request visible to the signed-in Custodian. */
    public Result<List<CheckoutViews.Request>> listRequests() {
        return execute(queries::listRequests);
    }

    /** Lists every checkout visible to the signed-in Custodian. */
    public Result<List<CheckoutViews.Checkout>> listCheckouts() {
        return execute(queries::listCheckouts);
    }

    /** Lists the ordered append-only history for the selected case. */
    public Result<List<HistoryViews.Event>> listHistory(CaseId caseId) {
        if (caseId == null) {
            return Result.failure("Select a case");
        }
        return execute(() -> history.listEventsForCase(caseId));
    }

    /** Approves the selected pending request. */
    public Result<Void> approve(CheckoutViews.Request request) {
        if (request == null) {
            return Result.failure("Select a pending request");
        }
        return executeVoid(() -> commands.approveRequest(
                new CheckoutCommands.ApproveRequest(request.requestId())));
    }

    /** Rejects the selected pending request. */
    public Result<Void> reject(CheckoutViews.Request request) {
        if (request == null) {
            return Result.failure("Select a pending request");
        }
        return executeVoid(() -> commands.rejectRequest(
                new CheckoutCommands.RejectRequest(request.requestId())));
    }

    /** Cancels the selected approved request with a documentary reason. */
    public Result<Void> cancel(CheckoutViews.Request request, String reason) {
        if (request == null) {
            return Result.failure("Select an approved request");
        }
        if (reason == null || reason.isBlank()) {
            return Result.failure("Cancellation reason is required");
        }
        return executeVoid(() -> commands.cancelApprovedRequest(
                new CheckoutCommands.CancelApprovedRequest(request.requestId(), reason)));
    }

    /** Records physical handoff for the selected approved request. */
    public Result<Void> recordHandoff(CheckoutViews.Request request) {
        if (request == null) {
            return Result.failure("Select an approved request");
        }
        return executeVoid(() -> commands.recordHandoff(
                new CheckoutCommands.RecordHandoff(request.requestId())));
    }

    /** Reverses the selected unacknowledged handoff with a reason. */
    public Result<Void> reverseHandoff(CheckoutViews.Request request, String reason) {
        if (request == null || request.handoffId().isEmpty()) {
            return Result.failure("Select an unacknowledged handoff");
        }
        if (reason == null || reason.isBlank()) {
            return Result.failure("Reversal reason is required");
        }
        return executeVoid(() -> commands.reverseHandoff(
                new CheckoutCommands.ReverseHandoff(request.handoffId().orElseThrow(), reason)));
    }

    /** Inspects a return that the collecting Investigator initiated. */
    public Result<Void> inspectReturn(CheckoutViews.Checkout checkout) {
        if (checkout == null) {
            return Result.failure("Select a return awaiting inspection");
        }
        return executeVoid(() -> commands.inspectReturn(
                new CheckoutCommands.InspectReturn(
                        checkout.checkoutId(), ReturnInspectionOutcome.STORED)));
    }

    /** Records and inspects an unplanned physical return with a reason. */
    public Result<Void> inspectUnplannedReturn(
            CheckoutViews.Checkout checkout, String reason) {
        if (checkout == null) {
            return Result.failure("Select an active checkout");
        }
        if (reason == null || reason.isBlank()) {
            return Result.failure("Unplanned return reason is required");
        }
        return executeVoid(() -> commands.inspectUnplannedReturn(
                new CheckoutCommands.InspectUnplannedReturn(
                        checkout.checkoutId(), ReturnInspectionOutcome.STORED, reason)));
    }

    private Result<Void> executeVoid(Runnable operation) {
        return execute(() -> {
            operation.run();
            return null;
        });
    }

    private static <T> Result<T> execute(Supplier<T> operation) {
        try {
            return Result.success(operation.get());
        } catch (IllegalArgumentException exception) {
            return Result.failure(exception.getMessage());
        } catch (ServiceException exception) {
            return Result.failure(ServiceFailurePresenter.messageFor(
                    exception, "Custodian checkout workflow"));
        }
    }

    /** Outcome rendered by Custodian workflow screens without exposing exceptions. */
    public record Result<T>(boolean successful, T value, String message) {
        /** Validates the presentation outcome. */
        public Result {
            Objects.requireNonNull(message, "message");
        }

        private static <T> Result<T> success(T value) {
            return new Result<>(true, value, "");
        }

        private static <T> Result<T> failure(String message) {
            return new Result<>(false, null, message);
        }
    }
}
