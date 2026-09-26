package evidencelogger.ui.investigator;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.service.ServiceException;
import evidencelogger.service.casework.CaseworkQueryService;
import evidencelogger.service.checkout.CheckoutCommandService;
import evidencelogger.service.checkout.CheckoutQueryService;
import evidencelogger.service.dto.CaseworkViews;
import evidencelogger.service.dto.CheckoutCommands;
import evidencelogger.service.dto.CheckoutViews;
import evidencelogger.service.dto.HistoryViews;
import evidencelogger.service.history.HistoryQueryService;
import evidencelogger.ui.common.ServiceFailurePresenter;

/** Presentation logic for assignment-scoped Investigator dashboard actions. */
public final class InvestigatorController {
    private final CaseworkQueryService caseworkQueries;
    private final CheckoutQueryService checkoutQueries;
    private final CheckoutCommandService commands;
    private final HistoryQueryService historyQueries;

    /** Creates a controller backed by all Investigator services. */
    public InvestigatorController(CaseworkQueryService caseworkQueries,
            CheckoutQueryService checkoutQueries, CheckoutCommandService commands,
            HistoryQueryService historyQueries) {
        this.caseworkQueries = Objects.requireNonNull(caseworkQueries, "caseworkQueries");
        this.checkoutQueries = Objects.requireNonNull(checkoutQueries, "checkoutQueries");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.historyQueries = Objects.requireNonNull(historyQueries, "historyQueries");
    }

    private InvestigatorController(CheckoutCommandService commands) {
        caseworkQueries = null;
        checkoutQueries = null;
        historyQueries = null;
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    /** Creates a command-only controller for form validation tests. */
    static InvestigatorController forCommands(CheckoutCommandService commands) {
        return new InvestigatorController(commands);
    }

    /** Searches cases visible to the signed-in Investigator. */
    public Result<List<CaseworkViews.Case>> searchCases(String text) {
        return execute(() -> caseworkQueries.searchCases(text));
    }

    /** Searches evidence visible to the signed-in Investigator. */
    public Result<List<CaseworkViews.Evidence>> searchEvidence(String text) {
        return execute(() -> caseworkQueries.searchEvidence(text));
    }

    /** Lists checkout requests visible to the signed-in Investigator. */
    public Result<List<CheckoutViews.Request>> listRequests() {
        return execute(checkoutQueries::listRequests);
    }

    /** Withdraws a pending request selected by the Investigator. */
    public Result<?> withdrawRequest(CheckoutRequestId id) {
        if (id == null) {
            return Result.failure("Select a pending request");
        }
        return execute(() -> {
            commands.withdrawRequest(new CheckoutCommands.WithdrawRequest(id));
            return null;
        });
    }

    /** Lists active checkouts visible to the signed-in Investigator. */
    public Result<List<CheckoutViews.Checkout>> listCheckouts() {
        return execute(checkoutQueries::listCheckouts);
    }

    /** Lists notes for the selected checkout. */
    public Result<List<CheckoutViews.ExaminationNote>> listNotes(CheckoutId id) {
        return execute(() -> checkoutQueries.listNotes(id));
    }

    /** Lists custody history for the selected case. */
    public Result<List<HistoryViews.Event>> listHistory(CaseId id) {
        return execute(() -> historyQueries.listEventsForCase(id));
    }

    /** Acknowledges collection of a checkout request. */
    public Result<?> acknowledge(HandoffId id) {
        if (id == null) {
            return Result.failure("Select a request awaiting collection");
        }
        return execute(() -> commands.acknowledgeCollection(
                new CheckoutCommands.AcknowledgeCollection(id)));
    }

    /** Adds an examination note to an active checkout. */
    public Result<?> addNote(CheckoutId id, String text) {
        if (id == null) {
            return Result.failure("Select an active checkout");
        }
        if (text == null || text.isBlank()) {
            return Result.failure("Examination note is required");
        }
        return execute(() -> commands.addExaminationNote(
                new CheckoutCommands.AddExaminationNote(id, text)));
    }

    /** Corrects an examination note with a required reason. */
    public Result<?> correctNote(ExaminationNoteId id, String text, String reason) {
        if (id == null) {
            return Result.failure("Select your examination note");
        }
        if (text == null || text.isBlank() || reason == null || reason.isBlank()) {
            return Result.failure("Correction text and reason are required");
        }
        return execute(() -> {
            commands.correctExaminationNote(
                    new CheckoutCommands.CorrectExaminationNote(id, text, reason));
            return null;
        });
    }

    /** Initiates return of an active checkout. */
    public Result<?> initiateReturn(CheckoutId id) {
        if (id == null) {
            return Result.failure("Select an active checkout");
        }
        return execute(() -> {
            commands.initiateReturn(new CheckoutCommands.InitiateReturn(id));
            return null;
        });
    }

    /** Submits a checkout request after validating its form values. */
    public Result<?> submitRequest(CaseworkViews.Evidence evidence,
            String purpose, String time) {
        if (evidence == null) {
            return Result.failure("Select evidence before submitting a request");
        }
        if (purpose == null || purpose.isBlank()) {
            return Result.failure("Purpose is required");
        }
        if (time == null || time.isBlank()) {
            return Result.failure(
                    "Expected return must be a UTC timestamp, for example "
                            + "2026-09-26T17:00:00Z");
        }
        try {
            return execute(() -> commands.submitRequest(new CheckoutCommands.SubmitRequest(
                    evidence.evidenceId(), purpose.strip(), Instant.parse(time.strip()))));
        } catch (DateTimeParseException exception) {
            return Result.failure(
                    "Expected return must be a UTC timestamp, for example "
                            + "2026-09-26T17:00:00Z");
        }
    }

    private static <T> Result<T> execute(Supplier<T> operation) {
        try {
            return Result.success(operation.get());
        } catch (ServiceException exception) {
            return Result.failure(ServiceFailurePresenter.messageFor(
                    exception, "Investigator workspace"));
        }
    }

    /** Outcome rendered by the Investigator screen without exposing service exceptions. */
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
