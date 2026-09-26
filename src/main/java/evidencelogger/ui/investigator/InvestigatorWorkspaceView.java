package evidencelogger.ui.investigator;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.ExaminationNoteId;
import evidencelogger.domain.HandoffId;
import evidencelogger.service.dto.CaseworkViews;
import evidencelogger.service.dto.CheckoutViews;
import evidencelogger.service.dto.HistoryViews;
import evidencelogger.ui.common.WorkspaceHeader;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/** Mockup-inspired Investigator dashboard for authorized casework and requests. */
public final class InvestigatorWorkspaceView {
    private static final double SECTION_HEADING_FONT_SIZE = 16;
    private static final DateTimeFormatter CASE_CREATED_AT_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/uuuu HH:mm")
            .withZone(ZoneOffset.UTC);

    private final InvestigatorController controller;
    private final Executor databaseExecutor;
    private final BorderPane root;
    private final Label status = new Label("Loading assigned workspace...");
    private final ListView<CaseworkViews.Case> cases = new ListView<>();
    private final ListView<CaseworkViews.Evidence> evidence = new ListView<>();
    private final ListView<CheckoutViews.Request> requests = new ListView<>();
    private final ListView<CheckoutViews.Checkout> checkouts = new ListView<>();
    private final ListView<CheckoutViews.ExaminationNote> notes = new ListView<>();
    private final ListView<HistoryViews.Event> history = new ListView<>();
    private Button submitRequest;
    private Button withdrawRequest;
    private Button acknowledgeCollection;
    private Button addNote;
    private Button correctNote;
    private Button initiateReturn;
    private TextArea noteEditor;
    private TextField correctionText;
    private TextField correctionReason;

    /** Creates the Investigator workspace and starts its initial data load. */
    public InvestigatorWorkspaceView(InvestigatorController controller,
            Executor databaseExecutor, String displayName, Runnable signOut) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        Objects.requireNonNull(signOut, "signOut");
        BorderPane workspace = new BorderPane();
        workspace.setTop(WorkspaceHeader.create(new WorkspaceHeader.Configuration(
                "Investigator Workspace", displayName, signOut)));
        workspace.setCenter(dashboard());
        workspace.setBottom(status);
        BorderPane.setMargin(status, new Insets(8));
        root = workspace;
        load();
    }

    /** Returns the root node for role-specific navigation. */
    public Parent view() {
        return root;
    }

    /** Builds the Investigator dashboard and wires selection-dependent workflow actions. */
    private Parent dashboard() {
        TextField search = new TextField();
        search.setPromptText("Search assigned cases and evidence");
        cases.setCellFactory(ignored -> caseCell());
        evidence.setCellFactory(ignored -> evidenceCell());
        requests.setCellFactory(ignored -> requestCell());
        Button find = new Button("Search");
        find.setOnAction(event -> load(search.getText()));
        HBox searchRow = new HBox(8, search, find);
        HBox.setHgrow(search, Priority.ALWAYS);
        TextField purpose = new TextField();
        purpose.setPromptText("Purpose");
        TextField expected = new TextField();
        expected.setPromptText("Expected return UTC (2026-09-26T17:00:00Z)");
        submitRequest = new Button("Submit Checkout Request");
        VBox requestFields = new VBox(8, purpose, expected);
        HBox.setHgrow(requestFields, Priority.ALWAYS);
        submitRequest.setMaxHeight(Double.MAX_VALUE);
        HBox requestRow = new HBox(8, requestFields, submitRequest);
        withdrawRequest = new Button("Withdraw pending request");
        submitRequest.setOnAction(event -> run(submitRequest, () -> controller.submitRequest(
                evidence.getSelectionModel().getSelectedItem(),
                        purpose.getText(), expected.getText()),
                ignored -> {
                    purpose.clear();
                    expected.clear();
                    load();
                }));
        withdrawRequest.setOnAction(event -> run(withdrawRequest, () -> controller.withdrawRequest(
                selectedRequestId()), ignored -> load()));
        evidence.getSelectionModel().selectedItemProperty().addListener((
                observable, oldEvidence, selectedEvidence) -> updateActionAvailability());
        requests.getSelectionModel().selectedItemProperty().addListener((
                observable, oldRequest, selectedRequest) -> updateActionAvailability());

        VBox left = panel("My Assigned Cases", searchRow, cases,
                sectionHeading("Evidence for Selected Case"), evidence);
        noteEditor = new TextArea();
        noteEditor.setPromptText("Examination note");
        addNote = new Button("Add examination note");
        acknowledgeCollection = new Button("Acknowledge collection");
        correctionText = new TextField();
        correctionText.setPromptText("Correction text");
        correctionReason = new TextField();
        correctionReason.setPromptText("Correction reason");
        correctNote = new Button("Correct note");
        initiateReturn = new Button("Initiate return");
        addNote.setOnAction(event -> run(addNote, () -> controller.addNote(
                selectedCheckoutId(), noteEditor.getText()),
                ignored -> {
                    noteEditor.clear();
                    load();
                }));
        initiateReturn.setOnAction(event -> run(initiateReturn, () -> controller.initiateReturn(
                selectedCheckoutId()),
                ignored -> {
                    updateActionAvailability();
                    status.setText("Notes are frozen while this return awaits Custodian inspection.");
                    load();
                }));
        acknowledgeCollection.setOnAction(event -> run(
                acknowledgeCollection, () -> controller.acknowledge(selectedHandoffId()),
                ignored -> load()));
        correctNote.setOnAction(event -> run(correctNote, () -> controller.correctNote(
                selectedNoteId(), correctionText.getText(), correctionReason.getText()),
                ignored -> {
                    correctionText.clear();
                    correctionReason.clear();
                    loadNotes();
                }));
        checkouts.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldCheckout, selectedCheckout) -> {
                    loadNotes();
                    updateActionAvailability();
                });
        notes.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldNote, selectedNote) -> updateActionAvailability());
        cases.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldCase, selectedCase) -> {
                    if (selectedCase != null) {
                        run(null, () -> controller.listEvidenceForCase(selectedCase.caseId()),
                                value -> evidence.setItems(FXCollections.observableArrayList(value)));
                        run(null, () -> controller.listHistory(selectedCase.caseId()),
                                value -> history.setItems(FXCollections.observableArrayList(value)));
                    } else {
                        evidence.setItems(FXCollections.observableArrayList());
                    }
                });

        VBox right = panel("Evidence Details & Request",
                new Label("Select assigned evidence to request."), requestRow,
                sectionHeading("My Requests"), requests, withdrawRequest,
                acknowledgeCollection, sectionHeading("Active Checkout"), checkouts, notes,
                noteEditor, addNote, correctionText, correctionReason, correctNote,
                initiateReturn, sectionHeading("Custody History"), history);
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.add(left, 0, 0);
        grid.add(right, 1, 0);
        GridPane.setHgrow(left, Priority.ALWAYS);
        GridPane.setHgrow(right, Priority.ALWAYS);
        updateActionAvailability();
        return grid;
    }

    private static VBox panel(String title, Node... nodes) {
        VBox box = new VBox(8);
        box.getChildren().add(sectionHeading(title));
        box.getChildren().addAll(nodes);
        box.setPadding(new Insets(12));
        Node growNode = nodes.length > 0 ? nodes[nodes.length - 1] : box;
        VBox.setVgrow(growNode, Priority.ALWAYS);
        return box;
    }

    private static Label sectionHeading(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, SECTION_HEADING_FONT_SIZE));
        return label;
    }

    /** Creates the two-line presentation for an assigned case. */
    private static ListCell<CaseworkViews.Case> caseCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(CaseworkViews.Case item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label title = boldLabel(item.title());
                Label createdAt = new Label("Created at: " + formatCaseCreatedAt(item.createdAt()));
                setText(null);
                setGraphic(new VBox(2, title, createdAt));
            }
        };
    }

    /** Formats assigned-case creation timestamps in the Investigator display. */
    static String formatCaseCreatedAt(Instant createdAt) {
        return CASE_CREATED_AT_FORMAT.format(Objects.requireNonNull(createdAt, "createdAt"));
    }

    /** Creates the three-line presentation for assigned evidence. */
    private static ListCell<CaseworkViews.Evidence> evidenceCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(CaseworkViews.Evidence item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label description = boldLabel(item.description());
                Label location = new Label("Storage: " + item.storageLocationName());
                Label custodyState = new Label(item.custodyState().name());
                custodyState.setTextFill(custodyStateColor(item.custodyState()));
                VBox details = new VBox(2, description, location);
                HBox evidenceRow = new HBox(8, details, custodyState);
                evidenceRow.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(details, Priority.ALWAYS);
                setText(null);
                setGraphic(evidenceRow);
            }
        };
    }

    private static Label boldLabel(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 12));
        return label;
    }

    /** Returns the custody-state colour used in the Investigator evidence list. */
    static Color custodyStateColor(EvidenceCustodyState custodyState) {
        return switch (Objects.requireNonNull(custodyState, "custodyState")) {
        case IN_STORAGE -> Color.GREEN;
        case HANDOFF_AWAITING_ACK, HANDIN_AWAITING_ACK -> Color.ORANGE;
        case CHECKED_OUT -> Color.RED;
        };
    }

    /** Creates the display for an Investigator's checkout request. */
    private static ListCell<CheckoutViews.Request> requestCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(CheckoutViews.Request item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                VBox details = new VBox(2,
                        boldLabel(item.evidenceDescription() + " @ " + item.storageLocationName()),
                        new Label("for Case " + item.caseTitle()),
                        new Label("Expected Return: "
                                + formatRequestExpectedReturn(item.expectedReturnAt())));
                Label requestStatus = new Label(item.status().name());
                requestStatus.setTextFill(requestStatusColor(item.status()));
                HBox requestRow = new HBox(8, details, requestStatus);
                requestRow.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(details, Priority.ALWAYS);
                setText(null);
                setGraphic(requestRow);
            }
        };
    }

    /** Formats a request's expected return time for the Investigator display. */
    static String formatRequestExpectedReturn(Instant expectedReturnAt) {
        return CASE_CREATED_AT_FORMAT.format(
                Objects.requireNonNull(expectedReturnAt, "expectedReturnAt"));
    }

    /** Returns the request-status colour used in the Investigator request list. */
    static Color requestStatusColor(CheckoutRequestStatus requestStatus) {
        return switch (Objects.requireNonNull(requestStatus, "requestStatus")) {
        case PENDING -> Color.ORANGE;
        case APPROVED, CONSUMED -> Color.GREEN;
        case REJECTED, WITHDRAWN, CANCELLED -> Color.RED;
        };
    }

    private void load() {
        load("");
    }

    /** Starts parallel workspace queries using the supplied assigned-case search text. */
    private void load(String text) {
        run(null, () -> controller.searchCases(text),
                value -> {
                    cases.setItems(FXCollections.observableArrayList(value));
                    evidence.setItems(FXCollections.observableArrayList());
                });
        run(null, controller::listRequests,
                value -> requests.setItems(FXCollections.observableArrayList(value)));
        run(null, controller::listCheckouts,
                value -> checkouts.setItems(FXCollections.observableArrayList(value)));
    }

    private CheckoutId selectedCheckoutId() {
        CheckoutViews.Checkout selected = checkouts.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.checkoutId();
    }

    private HandoffId selectedHandoffId() {
        CheckoutViews.Request selected = requests.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.handoffId().orElse(null);
    }

    private evidencelogger.domain.CheckoutRequestId selectedRequestId() {
        CheckoutViews.Request selected = requests.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.requestId();
    }

    private ExaminationNoteId selectedNoteId() {
        CheckoutViews.ExaminationNote selected = notes.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.noteId();
    }

    /** Reloads notes when an active checkout is selected. */
    private void loadNotes() {
        if (selectedCheckoutId() != null) {
            run(null, () -> controller.listNotes(selectedCheckoutId()),
                    value -> notes.setItems(FXCollections.observableArrayList(value)));
        }
    }

    /** Applies workflow action availability derived from the current selections. */
    private void updateActionAvailability() {
        ActionAvailability availability = actionAvailability(
                evidence.getSelectionModel().getSelectedItem(),
                requests.getSelectionModel().getSelectedItem(),
                checkouts.getSelectionModel().getSelectedItem(),
                notes.getSelectionModel().getSelectedItem());
        submitRequest.setDisable(!availability.canSubmitRequest());
        withdrawRequest.setDisable(!availability.canWithdrawRequest());
        acknowledgeCollection.setDisable(!availability.canAcknowledgeCollection());
        addNote.setDisable(!availability.canEditCheckout());
        initiateReturn.setDisable(!availability.canEditCheckout());
        noteEditor.setDisable(!availability.canEditCheckout());
        correctNote.setDisable(!availability.canCorrectNote());
    }

    /** Computes enabled presentation actions from the current read-model selections. */
    static ActionAvailability actionAvailability(
            CaseworkViews.Evidence selectedEvidence,
            CheckoutViews.Request selectedRequest,
            CheckoutViews.Checkout selectedCheckout,
            CheckoutViews.ExaminationNote selectedNote) {
        boolean editableCheckout = selectedCheckout != null
                && selectedCheckout.returnInitiatedAt().isEmpty()
                && selectedCheckout.completedAt().isEmpty()
                && selectedCheckout.evidenceState() == EvidenceCustodyState.CHECKED_OUT;
        return new ActionAvailability(
                selectedEvidence != null
                        && selectedEvidence.custodyState() == EvidenceCustodyState.IN_STORAGE,
                selectedRequest != null
                        && selectedRequest.status() == CheckoutRequestStatus.PENDING,
                selectedRequest != null
                        && selectedRequest.status() == CheckoutRequestStatus.APPROVED
                        && selectedRequest.handoffId().isPresent(),
                editableCheckout,
                selectedNote != null);
    }

    /** Presentation action state for the current Investigator selections. */
    record ActionAvailability(
            boolean canSubmitRequest,
            boolean canWithdrawRequest,
            boolean canAcknowledgeCollection,
            boolean canEditCheckout,
            boolean canCorrectNote) {
    }

    /** Dispatches service work and maps its result to dashboard status and button state. */
    private <T> void run(Button button, Supplier<InvestigatorController.Result<T>> task,
            Consumer<T> success) {
        dispatchTask(databaseExecutor, task,
                busy -> {
                    if (button != null) {
                        button.setDisable(busy);
                    }
                },
                Platform::runLater,
                value -> {
                    success.accept(value);
                    status.setText("Workspace refreshed");
                },
                status::setText);
    }

    /** Dispatches a database task and returns its result rendering to the UI boundary. */
    static <T> void dispatchTask(
            Executor executor,
            Supplier<InvestigatorController.Result<T>> task,
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
            InvestigatorController.Result<T> result = task.get();
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
}
