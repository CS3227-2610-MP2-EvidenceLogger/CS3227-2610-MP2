package evidencelogger.ui.custodian;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
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
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Custodian screens for decisions, handoffs, return inspection, and history. */
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
    private final ListView<CheckoutViews.Request> decisionRequests = new ListView<>();
    private final ListView<CheckoutViews.Request> handoffRequests = new ListView<>();
    private final ListView<CheckoutViews.Checkout> returnCheckouts = new ListView<>();
    private final ComboBox<CaseworkViews.Case> historyCase = new ComboBox<>();
    private final ListView<HistoryViews.Event> historyEvents = new ListView<>();
    private final Button approve = new Button("Approve");
    private final Button reject = new Button("Reject");
    private final Button cancel = new Button("Cancel approval");
    private final Button recordHandoff = new Button("Record handoff");
    private final Button reverseHandoff = new Button("Reverse handoff");
    private final Button inspectReturn = new Button("Inspect and store");
    private final Button inspectUnplanned = new Button("Record unplanned return");
    private final TextField cancellationReason = new TextField();
    private final TextField reversalReason = new TextField();
    private final TextField unplannedReason = new TextField();

    /** Creates the four A5 screens against the shared checkout interfaces. */
    public CustodianWorkflowView(
            CustodianWorkflowController controller,
            Executor databaseExecutor,
            Consumer<String> showStatus) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        this.showStatus = Objects.requireNonNull(showStatus, "showStatus");
        configureControls();
        refreshWorkflow();
    }

    /** Returns the nested workflow screens for the Custodian workspace. */
    public Parent view() {
        return new TabPane(
                fixedTab("Request decisions", decisionScreen()),
                fixedTab("Handoffs", handoffScreen()),
                fixedTab("Return inspection", returnScreen()),
                fixedTab("History", historyScreen()));
    }

    /** Supplies current case choices from the surrounding casework workspace. */
    public void showCases(List<CaseworkViews.Case> cases) {
        CaseworkViews.Case selected = historyCase.getValue();
        historyCase.setItems(FXCollections.observableArrayList(cases));
        if (selected != null && cases.contains(selected)) {
            historyCase.setValue(selected);
        } else if (!cases.isEmpty()) {
            historyCase.getSelectionModel().selectFirst();
        }
    }

    private Parent decisionScreen() {
        Button refresh = new Button("Refresh requests");
        refresh.setOnAction(event -> refreshRequests(refresh));
        approve.setOnAction(event -> run(approve, () ->
                controller.approve(selectedDecision()), ignored ->
                    refreshAfterAction("Request approved")));
        reject.setOnAction(event -> run(reject, () ->
                controller.reject(selectedDecision()), ignored ->
                    refreshAfterAction("Request rejected")));
        cancel.setOnAction(event -> run(cancel, () ->
                controller.cancel(selectedDecision(), cancellationReason.getText()),
                ignored -> {
                    cancellationReason.clear();
                    refreshAfterAction("Approval cancelled");
                }));
        VBox box = screen(
                "Approve or reject pending requests. Cancel an uncollected approval with a reason.",
                refresh,
                decisionRequests,
                row(approve, reject),
                row(cancellationReason, cancel));
        VBox.setVgrow(decisionRequests, Priority.ALWAYS);
        return box;
    }

    private Parent handoffScreen() {
        Button refresh = new Button("Refresh handoffs");
        refresh.setOnAction(event -> refreshRequests(refresh));
        recordHandoff.setOnAction(event -> run(recordHandoff, () ->
                controller.recordHandoff(selectedHandoff()),
                ignored -> refreshAfterAction("Handoff recorded")));
        reverseHandoff.setOnAction(event -> run(reverseHandoff, () ->
                controller.reverseHandoff(selectedHandoff(), reversalReason.getText()),
                ignored -> {
                    reversalReason.clear();
                    refreshAfterAction("Handoff reversed");
                }));
        VBox box = screen(
                "Record physical handoff after approval, or reverse an unacknowledged handoff.",
                refresh,
                handoffRequests,
                recordHandoff,
                row(reversalReason, reverseHandoff));
        VBox.setVgrow(handoffRequests, Priority.ALWAYS);
        return box;
    }

    private Parent returnScreen() {
        Button refresh = new Button("Refresh returns");
        refresh.setOnAction(event -> refreshCheckouts(refresh));
        inspectReturn.setOnAction(event -> run(inspectReturn, () ->
                controller.inspectReturn(selectedCheckout()),
                ignored -> refreshAfterAction("Return inspected and stored")));
        inspectUnplanned.setOnAction(event -> run(inspectUnplanned, () ->
                controller.inspectUnplannedReturn(
                        selectedCheckout(), unplannedReason.getText()),
                ignored -> {
                    unplannedReason.clear();
                    refreshAfterAction("Unplanned return inspected and stored");
                }));
        VBox box = screen(
                "Inspect initiated returns before storage, or record an unplanned return with a reason.",
                refresh,
                returnCheckouts,
                inspectReturn,
                row(unplannedReason, inspectUnplanned));
        VBox.setVgrow(returnCheckouts, Priority.ALWAYS);
        return box;
    }

    private Parent historyScreen() {
        Button load = new Button("Load history");
        load.setOnAction(event -> loadHistory(load));
        historyCase.setOnAction(event -> loadHistory(load));
        VBox box = screen(
                "Read append-only case history in its authorized service order.",
                row(historyCase, load),
                historyEvents);
        VBox.setVgrow(historyEvents, Priority.ALWAYS);
        return box;
    }

    private void configureControls() {
        cancellationReason.setPromptText("Cancellation reason");
        reversalReason.setPromptText("Reversal reason");
        unplannedReason.setPromptText("Unplanned return reason");
        decisionRequests.setCellFactory(list -> textCell(CustodianWorkflowView::requestText));
        handoffRequests.setCellFactory(list -> textCell(CustodianWorkflowView::requestText));
        returnCheckouts.setCellFactory(list -> textCell(CustodianWorkflowView::checkoutText));
        historyEvents.setCellFactory(list -> textCell(CustodianWorkflowView::historyText));
        historyCase.setConverter(converter(CaseworkViews.Case::title));
        historyCase.setMaxWidth(Double.MAX_VALUE);
        decisionRequests.getSelectionModel().selectedItemProperty().addListener((
                observable, oldValue, newValue) -> updateDecisionActions());
        handoffRequests.getSelectionModel().selectedItemProperty().addListener((
                observable, oldValue, newValue) -> updateHandoffActions());
        returnCheckouts.getSelectionModel().selectedItemProperty().addListener((
                observable, oldValue, newValue) -> updateReturnActions());
        updateDecisionActions();
        updateHandoffActions();
        updateReturnActions();
    }

    private void refreshWorkflow() {
        refreshRequests(null);
        refreshCheckouts(null);
    }

    private void refreshRequests(Button button) {
        run(button, controller::listRequests, requests -> {
            decisionRequests.setItems(FXCollections.observableArrayList(requests));
            handoffRequests.setItems(FXCollections.observableArrayList(requests));
            updateDecisionActions();
            updateHandoffActions();
        });
    }

    private void refreshCheckouts(Button button) {
        run(button, controller::listCheckouts, checkouts -> {
            returnCheckouts.setItems(FXCollections.observableArrayList(checkouts));
            updateReturnActions();
        });
    }

    private void loadHistory(Button button) {
        CaseworkViews.Case selected = historyCase.getValue();
        run(button, () -> controller.listHistory(selected == null ? null : selected.caseId()),
                events -> historyEvents.setItems(FXCollections.observableArrayList(events)));
    }

    private void refreshAfterAction(String message) {
        showStatus.accept(message);
        refreshWorkflow();
        if (historyCase.getValue() != null) {
            loadHistory(null);
        }
    }

    private CheckoutViews.Request selectedDecision() {
        return decisionRequests.getSelectionModel().getSelectedItem();
    }

    private CheckoutViews.Request selectedHandoff() {
        return handoffRequests.getSelectionModel().getSelectedItem();
    }

    private CheckoutViews.Checkout selectedCheckout() {
        return returnCheckouts.getSelectionModel().getSelectedItem();
    }

    private void updateDecisionActions() {
        ActionAvailability availability = actionAvailability(
                selectedDecision(), null);
        approve.setDisable(!availability.canDecide());
        reject.setDisable(!availability.canDecide());
        cancel.setDisable(!availability.canCancel());
    }

    private void updateHandoffActions() {
        ActionAvailability availability = actionAvailability(
                selectedHandoff(), null);
        recordHandoff.setDisable(!availability.canRecordHandoff());
        reverseHandoff.setDisable(!availability.canReverseHandoff());
    }

    private void updateReturnActions() {
        ActionAvailability availability = actionAvailability(
                null, selectedCheckout());
        inspectReturn.setDisable(!availability.canInspectReturn());
        inspectUnplanned.setDisable(!availability.canInspectUnplannedReturn());
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

    /** Presentation action state for the current Custodian selections. */
    record ActionAvailability(
            boolean canDecide,
            boolean canCancel,
            boolean canRecordHandoff,
            boolean canReverseHandoff,
            boolean canInspectReturn,
            boolean canInspectUnplannedReturn) {
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
                request.purpose(),
                TIME_FORMAT.format(request.expectedReturnAt()));
    }

    private static String checkoutText(CheckoutViews.Checkout checkout) {
        return String.format("%s | %s | collected by %s | %s",
                checkout.evidenceReference(), checkout.caseTitle(),
                checkout.collectorDisplayName(), checkout.evidenceState());
    }

    private static String historyText(HistoryViews.Event event) {
        String detail = event.correctionText().orElse(event.reason().orElse(""));
        return String.format("%s | %s | %s (%s)%s",
                TIME_FORMAT.format(event.eventTime()), event.type(),
                event.actorDisplayName(), event.actorRole(),
                detail.isBlank() ? "" : " | " + detail);
    }

    private static Tab fixedTab(String title, Parent content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private static VBox screen(String guidance, Node... content) {
        VBox box = new VBox(SPACING);
        box.setPadding(new Insets(16));
        Label help = new Label(guidance);
        help.setWrapText(true);
        box.getChildren().add(help);
        box.getChildren().addAll(content);
        return box;
    }

    private static HBox row(Node... content) {
        HBox box = new HBox(SPACING, content);
        for (Node node : content) {
            if (node instanceof TextField || node instanceof ComboBox<?>) {
                HBox.setHgrow(node, Priority.ALWAYS);
            }
        }
        return box;
    }

    private static <T> StringConverter<T> converter(java.util.function.Function<T, String> text) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : text.apply(value);
            }

            @Override
            public T fromString(String value) {
                return null;
            }
        };
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
