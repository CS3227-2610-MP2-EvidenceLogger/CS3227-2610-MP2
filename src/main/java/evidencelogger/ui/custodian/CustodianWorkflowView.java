package evidencelogger.ui.custodian;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.service.dto.CaseworkViews;
import evidencelogger.service.dto.CheckoutViews;
import evidencelogger.service.dto.HistoryViews;
import evidencelogger.ui.common.HistoryEventFormatter;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Task-oriented Custodian work queue and case-history panel. */
public final class CustodianWorkflowView {
    private static final double SPACING = 10;
    private static final Logger LOGGER = Logger.getLogger(
            CustodianWorkflowView.class.getName());
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm z")
            .withZone(ZoneId.systemDefault());

    private final CustodianWorkflowController controller;
    private final Executor databaseExecutor;
    private final Consumer<String> showStatus;
    private final TableView<WorkItem> workItems = new TableView<>();
    private final ComboBox<WorkflowCategory> category = new ComboBox<>();
    private final ListView<HistoryViews.Event> historyEvents = new ListView<>();
    private final Label historyTitle = new Label("Select a case to view history");
    private final Label actionHint = new Label("Select a task to see its available actions.");
    private final Button approve = new Button("Approve");
    private final Button reject = new Button("Reject");
    private final Button cancel = new Button("Cancel approval");
    private final Button recordHandoff = new Button("Record handoff");
    private final Button reverseHandoff = new Button("Reverse handoff");
    private final Button inspectReturn = new Button("Inspect and store");
    private final Button inspectUnplanned = new Button("Record unplanned return");
    private final Button correctHistory = new Button("Append correction");
    private final Parent workQueue;
    private final Parent historyPanel;
    private List<CheckoutViews.Request> requests = List.of();
    private List<CheckoutViews.Checkout> checkouts = List.of();
    private CaseworkViews.Case selectedHistoryCase;

    /** Creates the Custodian work queue and history panel. */
    public CustodianWorkflowView(
            CustodianWorkflowController controller,
            Executor databaseExecutor,
            Consumer<String> showStatus) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        this.showStatus = Objects.requireNonNull(showStatus, "showStatus");
        configureControls();
        workQueue = workQueueScreen();
        historyPanel = historyScreen();
        refreshWorkflow();
    }

    /** Returns the single task-oriented workflow screen. */
    public Parent view() {
        return workQueue;
    }

    /** Returns the history panel embedded in the selected-case workspace. */
    public Parent historyView() {
        return historyPanel;
    }

    /** Loads history for the case selected in the surrounding case workspace. */
    public void showHistoryForCase(CaseworkViews.Case selectedCase) {
        selectedHistoryCase = selectedCase;
        if (selectedCase == null) {
            historyTitle.setText("Select a case to view history");
            historyEvents.getItems().clear();
            updateHistoryAction();
            return;
        }
        historyTitle.setText("History for " + selectedCase.title());
        loadHistory(null);
    }

    /** Refreshes workflow tasks after casework changes that affect evidence. */
    public void refresh() {
        refreshWorkflow();
    }

    private Parent workQueueScreen() {
        Label title = new Label("Work queue");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label guidance = new Label(
                "Choose a task type. Only actions valid for the selected item are enabled.");
        guidance.setWrapText(true);
        Button refresh = new Button("Refresh work queue");
        refresh.setOnAction(event -> refreshWorkflow());

        HBox filters = new HBox(SPACING, new Label("Show"), category, refresh);
        HBox actions = new HBox(SPACING, approve, reject, cancel,
                recordHandoff, reverseHandoff, inspectReturn, inspectUnplanned);
        actionHint.setWrapText(true);

        VBox top = new VBox(SPACING, title, guidance, filters);
        VBox bottom = new VBox(SPACING, actionHint, actions);
        BorderPane pane = new BorderPane(workItems);
        pane.setTop(top);
        pane.setBottom(bottom);
        pane.setPadding(new Insets(16));
        BorderPane.setMargin(workItems, new Insets(SPACING, 0, SPACING, 0));
        return pane;
    }

    private Parent historyScreen() {
        historyTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        historyEvents.setPlaceholder(new Label("No history entries for this case."));
        historyEvents.setCellFactory(list -> textCell(HistoryEventFormatter::format));
        historyEvents.getSelectionModel().selectedItemProperty().addListener((
                observable, oldValue, newValue) -> updateHistoryAction());
        correctHistory.setOnAction(event -> promptText(
                "Append history correction",
                "Correction text",
                correction -> promptText(
                        "Why is this correction needed?",
                        "Correction reason",
                        reason -> run(correctHistory, () -> controller.correctHistory(
                                historyEvents.getSelectionModel().getSelectedItem(),
                                correction,
                                reason), ignored -> {
                                    showStatus.accept("History correction appended");
                                    loadHistory(null);
                                }))));
        Button refresh = new Button("Refresh history");
        refresh.setOnAction(event -> loadHistory(refresh));
        HBox controls = new HBox(SPACING, refresh, correctHistory);
        VBox box = new VBox(SPACING, historyTitle, historyEvents, controls);
        VBox.setVgrow(historyEvents, Priority.ALWAYS);
        box.setPadding(new Insets(12, 0, 0, 0));
        updateHistoryAction();
        return box;
    }

    private void configureControls() {
        category.setItems(FXCollections.observableArrayList(WorkflowCategory.values()));
        category.getSelectionModel().select(WorkflowCategory.PENDING_DECISIONS);
        category.setMaxWidth(Double.MAX_VALUE);
        category.setOnAction(event -> rebuildWorkItems());
        configureWorkTable();
        workItems.getSelectionModel().selectedItemProperty().addListener((
                observable, oldValue, newValue) -> updateActions());

        approve.setOnAction(event -> confirm(
                "Approve checkout request",
                "Approve the selected request?", () ->
                    run(approve, () -> controller.approve(selectedRequest()),
                        ignored -> refreshAfterAction("Request approved"))));
        reject.setOnAction(event -> confirm(
                "Reject checkout request",
                "Reject the selected request?", () ->
                    run(reject, () -> controller.reject(selectedRequest()),
                        ignored -> refreshAfterAction("Request rejected"))));
        cancel.setOnAction(event -> promptText(
                "Cancel approval",
                "Cancellation reason",
                reason -> confirm(
                        "Cancel approved request",
                        "Cancel this approval? The reason will be recorded in history.", () ->
                            run(cancel, () -> controller.cancel(selectedRequest(), reason),
                                ignored -> refreshAfterAction("Approval cancelled")))));
        recordHandoff.setOnAction(event -> confirm(
                "Record physical handoff",
                "Confirm that the selected evidence was offered for physical collection.", () ->
                    run(recordHandoff, () -> controller.recordHandoff(selectedRequest()),
                        ignored -> refreshAfterAction("Handoff recorded"))));
        reverseHandoff.setOnAction(event -> promptText(
                "Reverse handoff",
                "Reversal reason",
                reason -> confirm(
                        "Reverse recorded handoff",
                        "Reverse this unacknowledged handoff?", () ->
                            run(reverseHandoff, () ->
                                    controller.reverseHandoff(selectedRequest(), reason),
                                ignored -> refreshAfterAction("Handoff reversed")))));
        inspectReturn.setOnAction(event -> confirm(
                "Inspect and store return",
                "Confirm that the physical item was received, inspected, and stored.", () ->
                    run(inspectReturn, () -> controller.inspectReturn(selectedCheckout()),
                        ignored -> refreshAfterAction("Return inspected and stored"))));
        inspectUnplanned.setOnAction(event -> promptText(
                "Record unplanned return",
                "Unplanned return reason",
                reason -> confirm(
                        "Inspect and store unplanned return",
                        "Confirm physical receipt and inspection of this unplanned return.", () ->
                            run(inspectUnplanned, () -> controller.inspectUnplannedReturn(
                                        selectedCheckout(), reason),
                                ignored -> refreshAfterAction(
                                        "Unplanned return inspected and stored")))));
        updateActions();
    }

    private void configureWorkTable() {
        workItems.setPlaceholder(new Label("No items need attention in this category."));
        workItems.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        workItems.getColumns().add(column("Evidence", WorkItem::evidenceReference));
        workItems.getColumns().add(column("Case", WorkItem::caseTitle));
        workItems.getColumns().add(column("Investigator", WorkItem::investigator));
        workItems.getColumns().add(column("Status", WorkItem::status));
        workItems.getColumns().add(column("Due / collected", WorkItem::time));
        workItems.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(WorkItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                } else {
                    setStyle(getIndex() % 2 == 0
                            ? "-fx-background-color: #f2f4f7;"
                            : "-fx-background-color: white;");
                }
            }
        });
    }

    private void refreshWorkflow() {
        refreshRequests(null);
        refreshCheckouts(null);
    }

    private void refreshRequests(Button button) {
        run(button, controller::listRequests, value -> {
            requests = value;
            rebuildWorkItems();
        });
    }

    private void refreshCheckouts(Button button) {
        run(button, controller::listCheckouts, value -> {
            checkouts = value;
            rebuildWorkItems();
        });
    }

    private void rebuildWorkItems() {
        WorkflowCategory selectedCategory = category.getValue();
        if (selectedCategory == null) {
            return;
        }
        List<WorkItem> visible = new ArrayList<>();
        for (CheckoutViews.Request request : requests) {
            WorkflowCategory requestCategory = categoryFor(request);
            if (requestCategory != null
                    && (selectedCategory == WorkflowCategory.ALL_ACTIVE
                            || selectedCategory == requestCategory)) {
                visible.add(WorkItem.forRequest(request, requestCategory));
            }
        }
        for (CheckoutViews.Checkout checkout : checkouts) {
            WorkflowCategory checkoutCategory = categoryFor(checkout);
            if (checkoutCategory != null
                    && (selectedCategory == WorkflowCategory.ALL_ACTIVE
                            || selectedCategory == checkoutCategory)) {
                visible.add(WorkItem.forCheckout(checkout, checkoutCategory));
            }
        }
        workItems.setItems(FXCollections.observableArrayList(visible));
        updateActions();
    }

    private void loadHistory(Button button) {
        CaseworkViews.Case selected = selectedHistoryCase;
        if (selected == null) {
            historyEvents.getItems().clear();
            return;
        }
        run(button, () -> controller.listHistory(selected.caseId()),
                events -> historyEvents.setItems(FXCollections.observableArrayList(events)));
    }

    private void refreshAfterAction(String message) {
        showStatus.accept(message);
        refreshWorkflow();
        loadHistory(null);
    }

    private CheckoutViews.Request selectedRequest() {
        WorkItem selected = workItems.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.request().orElse(null);
    }

    private CheckoutViews.Checkout selectedCheckout() {
        WorkItem selected = workItems.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.checkout().orElse(null);
    }

    private void updateActions() {
        ActionAvailability availability = actionAvailability(
                selectedRequest(), selectedCheckout());
        approve.setDisable(!availability.canDecide());
        reject.setDisable(!availability.canDecide());
        cancel.setDisable(!availability.canCancel());
        recordHandoff.setDisable(!availability.canRecordHandoff());
        reverseHandoff.setDisable(!availability.canReverseHandoff());
        inspectReturn.setDisable(!availability.canInspectReturn());
        inspectUnplanned.setDisable(!availability.canInspectUnplannedReturn());
        WorkItem selected = workItems.getSelectionModel().getSelectedItem();
        actionHint.setText(selected == null
                ? "Select a task to see its available actions."
                : selected.guidance());
    }

    private void updateHistoryAction() {
        correctHistory.setDisable(
                historyEvents.getSelectionModel().getSelectedItem() == null);
    }

    /** Computes enabled Custodian actions from current shared read models. */
    static ActionAvailability actionAvailability(
            CheckoutViews.Request request,
            CheckoutViews.Checkout checkout) {
        boolean pending = request != null
                && request.status() == CheckoutRequestStatus.PENDING;
        boolean approved = request != null
                && request.status() == CheckoutRequestStatus.APPROVED;
        boolean awaitingAcknowledgement = approved
                && request.handoffId().isPresent()
                && request.evidenceState() == EvidenceCustodyState.HANDOFF_AWAITING_ACK;
        boolean initiatedReturn = checkout != null
                && checkout.completedAt().isEmpty()
                && checkout.returnInitiatedAt().isPresent()
                && checkout.evidenceState() == EvidenceCustodyState.HANDIN_AWAITING_ACK;
        boolean activeCheckout = checkout != null
                && checkout.completedAt().isEmpty()
                && checkout.returnInitiatedAt().isEmpty()
                && checkout.evidenceState() == EvidenceCustodyState.CHECKED_OUT;
        return new ActionAvailability(
                pending,
                approved && request.handoffId().isEmpty()
                        && request.evidenceState() == EvidenceCustodyState.IN_STORAGE,
                approved && request.handoffId().isEmpty(),
                awaitingAcknowledgement,
                initiatedReturn,
                activeCheckout);
    }

    /** Categorizes a request for the task filter, excluding terminal requests. */
    static WorkflowCategory categoryFor(CheckoutViews.Request request) {
        if (request.status() == CheckoutRequestStatus.PENDING) {
            return WorkflowCategory.PENDING_DECISIONS;
        }
        if (request.status() == CheckoutRequestStatus.APPROVED
                && request.handoffId().isEmpty()
                && request.evidenceState() == EvidenceCustodyState.IN_STORAGE) {
            return WorkflowCategory.READY_FOR_HANDOFF;
        }
        if (request.status() == CheckoutRequestStatus.APPROVED
                && request.handoffId().isPresent()
                && request.evidenceState() == EvidenceCustodyState.HANDOFF_AWAITING_ACK) {
            return WorkflowCategory.AWAITING_ACKNOWLEDGEMENT;
        }
        return null;
    }

    /** Categorizes an active checkout for the task filter. */
    static WorkflowCategory categoryFor(CheckoutViews.Checkout checkout) {
        if (checkout.completedAt().isPresent()) {
            return null;
        }
        if (checkout.returnInitiatedAt().isPresent()
                && checkout.evidenceState() == EvidenceCustodyState.HANDIN_AWAITING_ACK) {
            return WorkflowCategory.RETURNS_TO_INSPECT;
        }
        if (checkout.returnInitiatedAt().isEmpty()
                && checkout.evidenceState() == EvidenceCustodyState.CHECKED_OUT) {
            return WorkflowCategory.CURRENTLY_CHECKED_OUT;
        }
        return null;
    }

    /** Presentation action state for the current selection. */
    record ActionAvailability(
            boolean canDecide,
            boolean canCancel,
            boolean canRecordHandoff,
            boolean canReverseHandoff,
            boolean canInspectReturn,
            boolean canInspectUnplannedReturn) {
    }

    enum WorkflowCategory {
        PENDING_DECISIONS("Pending decisions"),
        READY_FOR_HANDOFF("Ready for handoff"),
        AWAITING_ACKNOWLEDGEMENT("Awaiting acknowledgement"),
        RETURNS_TO_INSPECT("Returns to inspect"),
        CURRENTLY_CHECKED_OUT("Currently checked out"),
        ALL_ACTIVE("All active work");

        private final String label;

        WorkflowCategory(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record WorkItem(
            Optional<CheckoutViews.Request> request,
            Optional<CheckoutViews.Checkout> checkout,
            WorkflowCategory category) {
        private static WorkItem forRequest(
                CheckoutViews.Request request, WorkflowCategory category) {
            return new WorkItem(Optional.of(request), Optional.empty(), category);
        }

        private static WorkItem forCheckout(
                CheckoutViews.Checkout checkout, WorkflowCategory category) {
            return new WorkItem(Optional.empty(), Optional.of(checkout), category);
        }

        private String evidenceReference() {
            return request.map(CheckoutViews.Request::evidenceReference)
                    .orElseGet(() -> checkout.orElseThrow().evidenceReference());
        }

        private String caseTitle() {
            return request.map(CheckoutViews.Request::caseTitle)
                    .orElseGet(() -> checkout.orElseThrow().caseTitle());
        }

        private String investigator() {
            return request.map(CheckoutViews.Request::requesterDisplayName)
                    .orElseGet(() -> checkout.orElseThrow().collectorDisplayName());
        }

        private String status() {
            return category.toString();
        }

        private String time() {
            return request.map(value -> TIME_FORMAT.format(value.expectedReturnAt()))
                    .orElseGet(() -> TIME_FORMAT.format(
                            checkout.orElseThrow().collectedAt()));
        }

        private String guidance() {
            return switch (category) {
            case PENDING_DECISIONS -> "Review the purpose and due date, then approve or reject.";
            case READY_FOR_HANDOFF -> "The request is approved and ready for physical handoff.";
            case AWAITING_ACKNOWLEDGEMENT ->
                "Waiting for Investigator acknowledgement. Reverse only if needed.";
            case RETURNS_TO_INSPECT ->
                "Physically inspect the returned item before storing it.";
            case CURRENTLY_CHECKED_OUT ->
                "The item is with the Investigator. Use unplanned return only after receipt.";
            case ALL_ACTIVE -> "Select a task to see its available actions.";
            };
        }
    }

    private <T> void run(
            Button button,
            Supplier<CustodianWorkflowController.Result<T>> task,
            Consumer<T> success) {
        dispatchTask(databaseExecutor, task,
                busy -> {
                    if (button != null) {
                        button.setDisable(busy);
                    }
                },
                Platform::runLater,
                success,
                showStatus);
    }

    /** Dispatches service work and returns result rendering to the JavaFX boundary. */
    static <T> void dispatchTask(
            Executor executor,
            Supplier<CustodianWorkflowController.Result<T>> task,
            Consumer<Boolean> setBusy,
            Consumer<Runnable> uiDispatcher,
            Consumer<T> success,
            Consumer<String> failure) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(setBusy, "setBusy");
        Objects.requireNonNull(uiDispatcher, "uiDispatcher");
        Objects.requireNonNull(success, "success");
        Objects.requireNonNull(failure, "failure");
        setBusy.accept(true);
        executor.execute(() -> {
            CustodianWorkflowController.Result<T> result;
            try {
                result = task.get();
            } catch (RuntimeException exception) {
                LOGGER.log(Level.SEVERE, "Unexpected Custodian workflow screen failure", exception);
                uiDispatcher.accept(() -> {
                    setBusy.accept(false);
                    failure.accept("The operation could not be completed. Please try again.");
                });
                return;
            }
            uiDispatcher.accept(() -> {
                setBusy.accept(false);
                if (result.successful()) {
                    success.accept(result.value());
                } else {
                    failure.accept(result.message());
                }
            });
        });
    }

    static String requestText(CheckoutViews.Request request) {
        return String.format("%s | %s | %s | %s | purpose: %s | due %s",
                request.evidenceReference(), request.caseTitle(),
                request.requesterDisplayName(), request.status(),
                request.purpose(), TIME_FORMAT.format(request.expectedReturnAt()));
    }

    private static TableColumn<WorkItem, String> column(
            String title, java.util.function.Function<WorkItem, String> value) {
        TableColumn<WorkItem, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private static void confirm(String title, String message, Runnable confirmedAction) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message,
                ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        if (alert.showAndWait().filter(ButtonType.OK::equals).isPresent()) {
            confirmedAction.run();
        }
    }

    private static void promptText(
            String title, String prompt, Consumer<String> acceptedText) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setContentText(prompt);
        dialog.showAndWait()
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .ifPresent(acceptedText);
    }

    private static <T> ListCell<T> textCell(
            java.util.function.Function<T, String> text) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : text.apply(item));
            }
        };
    }
}
