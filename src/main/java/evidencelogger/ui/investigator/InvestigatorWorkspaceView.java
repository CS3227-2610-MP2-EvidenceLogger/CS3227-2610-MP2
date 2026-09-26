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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

/** Mockup-inspired Investigator dashboard for authorized casework and requests. */
public final class InvestigatorWorkspaceView {
    private static final double MINIMUM_BODY_WIDTH = 1400;
    private static final double SECTION_HEADING_FONT_SIZE = 16;
    private static final int NOTE_PREVIEW_MAX_LENGTH = 60;
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
        workspace.setCenter(scrollableDashboard());
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
        checkouts.setCellFactory(ignored -> checkoutCell());
        notes.setCellFactory(ignored -> noteCell());
        history.setCellFactory(ignored -> historyCell());
        Button find = new Button("Search");
        find.setOnAction(event -> load(search.getText()));
        HBox searchRow = new HBox(8, search, find);
        HBox.setHgrow(search, Priority.ALWAYS);
        TextField purpose = new TextField();
        purpose.setPromptText("Purpose");
        Label purposeLabel = new Label("Purpose:");
        HBox purposeRow = new HBox(8, purposeLabel, purpose);
        HBox.setHgrow(purpose, Priority.ALWAYS);
        TextField expected = new TextField();
        expected.setPromptText("DD/MM/YYYY HH:MM");
        Label expectedLabel = new Label("Expected Return (Format: DD/MM/YYYY HH:MM):");
        HBox expectedRow = new HBox(8, expectedLabel, expected);
        HBox.setHgrow(expected, Priority.ALWAYS);
        Label requestError = new Label();
        requestError.setTextFill(Color.RED);
        requestError.setWrapText(true);
        submitRequest = new Button("Submit Checkout Request");
        VBox requestFields = new VBox(8, purposeRow, expectedRow);
        HBox.setHgrow(requestFields, Priority.ALWAYS);
        submitRequest.setMaxHeight(Double.MAX_VALUE);
        HBox requestRow = new HBox(8, requestFields, submitRequest);
        withdrawRequest = new Button("Withdraw PENDING Request");
        submitRequest.setOnAction(event -> {
            requestError.setText("");
            run(submitRequest, () -> controller.submitRequest(
                    evidence.getSelectionModel().getSelectedItem(),
                    purpose.getText(), expected.getText()),
                ignored -> {
                    purpose.clear();
                    expected.clear();
                    load();
                }, requestError::setText);
        });
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
        acknowledgeCollection = new Button("Acknowledge Collection");
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
                .addListener((observable, oldNote, selectedNote) -> {
                    showSelectedNote(selectedNote);
                    updateActionAvailability();
                });
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

        HBox requestActions = new HBox(8, withdrawRequest, acknowledgeCollection);
        VBox myRequests = new VBox(8,
                requests, requestActions, new Label(
                        "Select assigned evidence to request (in the "
                                + "`Evidence for Selected Case` section)."),
                requestError, requestRow);
        myRequests.setPadding(new Insets(12));
        VBox.setVgrow(requests, Priority.ALWAYS);
        VBox checkoutColumn = new VBox(8, sectionHeading("Checkouts"), checkouts,
                initiateReturn);
        VBox notesColumn = new VBox(8, sectionHeading("Examination Notes"), notes,
                noteEditor, addNote, correctionText, correctionReason, correctNote);
        VBox.setVgrow(notes, Priority.ALWAYS);
        VBox.setVgrow(checkouts, Priority.ALWAYS);
        HBox activeCheckout = new HBox(12, checkoutColumn, notesColumn);
        activeCheckout.setPadding(new Insets(12));
        HBox.setHgrow(checkoutColumn, Priority.ALWAYS);
        HBox.setHgrow(notesColumn, Priority.ALWAYS);
        TabPane workflowTabs = new TabPane(
                fixedTab("My Requests", myRequests),
                fixedTab("Active Checkout", activeCheckout));

        VBox right = panel("Evidence Details & Request",
                workflowTabs,
                sectionHeading("Custody History"), history);
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.setMinWidth(MINIMUM_BODY_WIDTH);
        grid.add(left, 0, 0);
        grid.add(right, 1, 0);
        GridPane.setHgrow(left, Priority.ALWAYS);
        GridPane.setHgrow(right, Priority.ALWAYS);
        updateActionAvailability();
        return grid;
    }

    /** Keeps the workspace header fixed while the body can scroll to its full width. */
    private Parent scrollableDashboard() {
        ScrollPane scrollPane = new ScrollPane(dashboard());
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scrollPane;
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

    /** Creates a Custodian-style fixed workflow tab. */
    private static Tab fixedTab(String title, Parent content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
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
                Label id = boldLabel(item.publicReference());
                Label description = new Label("Description: " + item.description());
                Label location = new Label("Storage: " + item.storageLocationName());
                Label custodyState = new Label(item.custodyState().name());
                custodyState.setTextFill(custodyStateColor(item.custodyState()));
                VBox details = new VBox(2, id, description, location);
                BorderPane evidenceRow = new BorderPane();
                evidenceRow.setLeft(details);
                evidenceRow.setBottom(custodyState);
                BorderPane.setAlignment(custodyState, Pos.BOTTOM_RIGHT);
                evidenceRow.setMaxWidth(Double.MAX_VALUE);
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
                        boldLabel("[" + item.caseTitle() + "] " + item.evidenceReference()),
                        new Label("Storage: " + item.storageLocationName()),
                        new Label("Description: " + item.evidenceDescription()),
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

    /** Creates the display for an Investigator's active checkout. */
    private static ListCell<CheckoutViews.Checkout> checkoutCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(CheckoutViews.Checkout item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                VBox details = new VBox(2,
                        boldLabel("[" + item.caseTitle() + "] " + item.evidenceReference()),
                        new Label("Collected: " + formatCheckoutTimestamp(item.collectedAt())));
                HBox checkoutRow = new HBox(8, details);
                checkoutRow.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(details, Priority.ALWAYS);
                item.returnInitiatedAt().ifPresent(returnInitiatedAt -> {
                    Label returnInitiated = new Label(
                            "Return Initiated: " + formatCheckoutTimestamp(returnInitiatedAt));
                    returnInitiated.setTextFill(Color.GREEN);
                    returnInitiated.setFont(Font.font("System", FontPosture.ITALIC, 12));
                    details.getChildren().add(returnInitiated);
                });
                setText(null);
                setGraphic(checkoutRow);
            }
        };
    }

    /** Creates the compact presentation for a note belonging to the selected checkout. */
    private static ListCell<CheckoutViews.ExaminationNote> noteCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(CheckoutViews.ExaminationNote item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                VBox details = new VBox(2,
                        boldLabel(notePreview(item.text())), new Label(noteByline(item)));
                BorderPane noteRow = new BorderPane();
                noteRow.setTop(details);
                noteRow.setMaxWidth(Double.MAX_VALUE);
                item.corrections().stream().findFirst().ifPresent(ignored -> {
                    Label correctionCount = new Label(correctionCountLabel(item));
                    noteRow.setBottom(correctionCount);
                    BorderPane.setAlignment(correctionCount, Pos.BOTTOM_RIGHT);
                });
                setText(null);
                setGraphic(noteRow);
            }
        };
    }

    /** Creates the compact presentation for an immutable custody-history event. */
    private static ListCell<HistoryViews.Event> historyCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(HistoryViews.Event item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                VBox details = new VBox(2,
                        boldLabel(historyEventName(item)), new Label(historyByline(item)));
                BorderPane historyRow = new BorderPane();
                historyRow.setTop(details);
                historyRow.setMaxWidth(Double.MAX_VALUE);
                if (isCorrectionEvent(item)) {
                    Label corrected = new Label("Corrected");
                    historyRow.setBottom(corrected);
                    BorderPane.setAlignment(corrected, Pos.BOTTOM_RIGHT);
                }
                setText(null);
                setGraphic(historyRow);
            }
        };
    }

    /** Converts an audit event type to the readable name shown in custody history. */
    static String historyEventName(HistoryViews.Event event) {
        return switch (Objects.requireNonNull(event, "event").type()) {
        case CASE_CREATED -> "Case created";
        case CASE_ASSIGNED -> "Case assigned";
        case CASE_UNASSIGNED -> "Case unassigned";
        case LOCATION_ADDED -> "Location added";
        case EVIDENCE_REGISTERED -> "Evidence registered";
        case REQUEST_SUBMITTED -> "Request submitted";
        case REQUEST_WITHDRAWN -> "Request withdrawn";
        case REQUEST_APPROVED -> "Request approved";
        case REQUEST_REJECTED -> "Request rejected";
        case REQUEST_CANCELLED -> "Request cancelled";
        case HANDOFF_RECORDED -> "Handoff recorded";
        case HANDOFF_REVERSED -> "Handoff reversed";
        case COLLECTION_ACKNOWLEDGED -> "Collection acknowledged";
        case EXAMINATION_NOTE_ADDED -> "Examination note added";
        case RETURN_INITIATED -> "Return initiated";
        case RETURN_INSPECTED_STORED -> "Return inspected and stored";
        case UNPLANNED_RETURN_INSPECTED -> "Unplanned return inspected";
        case HISTORY_CORRECTED -> "History corrected";
        case EXAMINATION_NOTE_CORRECTED -> "Examination note corrected";
        };
    }

    /** Formats actor and time metadata for a custody-history entry. */
    static String historyByline(HistoryViews.Event event) {
        Objects.requireNonNull(event, "event");
        return event.actorDisplayName() + " · " + formatCheckoutTimestamp(event.eventTime());
    }

    /** Determines whether a history entry represents an append-only correction. */
    static boolean isCorrectionEvent(HistoryViews.Event event) {
        return switch (Objects.requireNonNull(event, "event").type()) {
        case HISTORY_CORRECTED, EXAMINATION_NOTE_CORRECTED -> true;
        default -> false;
        };
    }

    /** Produces the one-line note preview shown in the selected checkout's note list. */
    static String notePreview(String text) {
        String firstLine = Objects.requireNonNull(text, "text").lines()
                .findFirst()
                .orElse("")
                .strip();
        if (firstLine.length() <= NOTE_PREVIEW_MAX_LENGTH) {
            return text.contains("\n") ? firstLine + "…" : firstLine;
        }
        return firstLine.substring(0, NOTE_PREVIEW_MAX_LENGTH - 1) + "…";
    }

    /** Formats the secondary author and time metadata for a note entry. */
    static String noteByline(CheckoutViews.ExaminationNote note) {
        Objects.requireNonNull(note, "note");
        return note.authorDisplayName() + " · " + formatCheckoutTimestamp(note.createdAt());
    }

    /** Formats the optional correction count shown in the note entry's lower-right corner. */
    static String correctionCountLabel(CheckoutViews.ExaminationNote note) {
        int correctionCount = Objects.requireNonNull(note, "note").corrections().size();
        return correctionCount + (correctionCount == 1 ? " correction" : " corrections");
    }

    /** Formats the full selected note and every immutable correction for the disabled viewer. */
    static String formatSelectedNote(CheckoutViews.ExaminationNote note) {
        Objects.requireNonNull(note, "note");
        StringBuilder formatted = new StringBuilder(note.text());
        String divider = "\n------------------------------------------------------";
        formatted.append(divider);
        for (CheckoutViews.NoteCorrection correction : note.corrections()) {
            formatted.append("\nCorrection by ")
                    .append(correction.authorDisplayName())
                    .append(" · ")
                    .append(formatCheckoutTimestamp(correction.createdAt()))
                    .append("\n")
                    .append(correction.correctionText())
                    .append("\nReason: ")
                    .append(correction.reason())
                    .append(divider);
        }
        return formatted.toString();
    }

    /** Formats checkout timestamps for the Investigator display. */
    static String formatCheckoutTimestamp(Instant timestamp) {
        return CASE_CREATED_AT_FORMAT.format(Objects.requireNonNull(timestamp, "timestamp"));
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
        CheckoutId checkoutId = selectedCheckoutId();
        notes.getSelectionModel().clearSelection();
        noteEditor.clear();
        if (checkoutId == null) {
            notes.setItems(FXCollections.observableArrayList());
            return;
        }
        run(null, () -> controller.listNotes(checkoutId), value -> {
            if (checkoutId.equals(selectedCheckoutId())) {
                notes.setItems(FXCollections.observableArrayList(value));
            }
        });
    }

    /** Displays the full note and corrections while preserving disabled state after return initiation. */
    private void showSelectedNote(CheckoutViews.ExaminationNote selectedNote) {
        noteEditor.setText(selectedNote == null ? "" : formatSelectedNote(selectedNote));
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
        run(button, task, success, status::setText);
    }

    private <T> void run(Button button, Supplier<InvestigatorController.Result<T>> task,
            Consumer<T> success, Consumer<String> failure) {
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
                failure);
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
