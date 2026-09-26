package evidencelogger.ui.custodian;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.service.dto.CaseworkViews;
import evidencelogger.ui.common.WorkspaceHeader;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Case-first Custodian workspace for casework, evidence, tasks, and locations. */
public final class CustodianCaseworkView {
    private static final double SPACING = 10;
    private static final Logger LOGGER = Logger.getLogger(
            CustodianCaseworkView.class.getName());
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm z")
            .withZone(ZoneId.systemDefault());
    private static final List<String> EVIDENCE_COLUMNS =
            List.of("Description", "Location", "Case", "Status");

    private final CaseworkController controller;
    private final Executor databaseExecutor;
    private final BorderPane root;
    private final Label status = new Label("Loading casework data...");
    private final ListView<CaseworkViews.Case> caseResults = new ListView<>();
    private final ListView<CaseworkViews.Investigator> currentAssignments = new ListView<>();
    private final ListView<CaseworkViews.StorageLocation> locationResults = new ListView<>();
    private final TableView<CaseworkViews.Evidence> caseEvidence = evidenceTable();
    private final TableView<CaseworkViews.Evidence> evidenceResults = evidenceTable();
    private final ComboBox<CaseworkViews.Investigator> assignmentInvestigator =
            investigatorComboBox();
    private final ComboBox<CaseworkViews.Case> caseFilter = caseComboBox();
    private final ComboBox<CaseworkViews.StorageLocation> locationFilter = locationComboBox();
    private final ComboBox<EvidenceCustodyState> stateFilter = new ComboBox<>();
    private final CheckBox includeVoided = new CheckBox("Include voided registrations");
    private final TextField evidenceSearch = new TextField();
    private final Label selectedCaseTitle = new Label("Select a case");
    private final Label evidenceDetails = new Label("Select evidence to view its reference.");
    private final Label voidGuidance = new Label(
            "Voiding is available only for in-storage items with no checkout request history.");
    private final Button removeAssignment = new Button("Remove selected investigator");
    private final Button registerEvidence = new Button("Register evidence");
    private final Button copyReference = new Button("Copy reference");
    private final Button voidEvidence = new Button("Void erroneous registration");
    private final CustodianWorkflowView workflowView;
    private List<CaseworkViews.Case> cases = List.of();
    private List<CaseworkViews.Investigator> investigators = List.of();
    private List<CaseworkViews.StorageLocation> locations = List.of();
    private List<CaseworkViews.Evidence> evidence = List.of();

    /** Creates the Custodian workspace and starts loading its reference data. */
    public CustodianCaseworkView(
            CaseworkController controller,
            CustodianWorkflowController workflowController,
            Executor databaseExecutor,
            String displayName,
            Runnable signOut) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        workflowView = new CustodianWorkflowView(
                Objects.requireNonNull(workflowController, "workflowController"),
                databaseExecutor,
                this::showStatus);
        configureControls();

        TabPane tabs = new TabPane(
                fixedTab("Cases", caseScreen()),
                fixedTab("Evidence", evidenceScreen()),
                fixedTab("Work queue", workflowView.view()),
                fixedTab("Locations", locationScreen()));
        root = new BorderPane(tabs);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setStyle("-fx-background-color: #e8eef6; -fx-padding: 8;");
        VBox header = new VBox(WorkspaceHeader.create(
                headerConfiguration(displayName, signOut)), status);
        root.setTop(header);
        refreshReferenceData();
    }

    /** Returns the root node for role-specific navigation. */
    public Parent view() {
        return root;
    }

    static WorkspaceHeader.Configuration headerConfiguration(
            String displayName, Runnable signOut) {
        return new WorkspaceHeader.Configuration("Custodian Workspace", displayName, signOut);
    }

    private Parent caseScreen() {
        TextField search = new TextField();
        search.setPromptText("Search cases");
        Button find = new Button("Search");
        find.setOnAction(event -> run(find, () -> controller.searchCases(search.getText()),
                this::showCases, "Cases refreshed"));
        Button newCase = new Button("New case");
        newCase.setOnAction(event -> showCreateCaseDialog());
        caseResults.setPlaceholder(new Label("No cases match this search."));

        VBox left = new VBox(SPACING, new Label("Cases"), row(search, find), newCase, caseResults);
        left.setPadding(new Insets(16));
        left.setMinWidth(250);
        VBox.setVgrow(caseResults, Priority.ALWAYS);

        selectedCaseTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Button assign = new Button("Assign investigator");
        assign.setOnAction(event -> assignInvestigator(assign));
        removeAssignment.setOnAction(event -> removeAssignment(removeAssignment));
        HBox assignmentActions = row(assignmentInvestigator, assign, removeAssignment);
        VBox assignmentBox = new VBox(SPACING, assignmentActions, currentAssignments);
        currentAssignments.setPrefHeight(100);
        TitledPane assignmentsPane = new TitledPane("Assigned investigators", assignmentBox);
        assignmentsPane.setCollapsible(true);
        assignmentsPane.setExpanded(true);

        registerEvidence.setOnAction(event -> showRegistrationDialog(selectedCase()));
        Button copyCaseEvidenceReference = new Button("Copy selected reference");
        copyCaseEvidenceReference.setOnAction(event ->
                copyReference(caseEvidence.getSelectionModel().getSelectedItem()));
        HBox caseEvidenceActions = new HBox(
                SPACING, registerEvidence, copyCaseEvidenceReference);
        caseEvidence.setPlaceholder(new Label("No evidence is registered to this case."));
        TitledPane historyPane = new TitledPane("Case history", workflowView.historyView());
        historyPane.setCollapsible(true);
        historyPane.setExpanded(false);

        VBox right = new VBox(SPACING,
                selectedCaseTitle,
                assignmentsPane,
                new Label("Evidence in this case"),
                caseEvidence,
                caseEvidenceActions,
                historyPane);
        right.setPadding(new Insets(16));
        VBox.setVgrow(caseEvidence, Priority.ALWAYS);
        VBox.setVgrow(historyPane, Priority.ALWAYS);

        SplitPane split = new SplitPane(left, right);
        split.setDividerPositions(0.28);
        return split;
    }

    private Parent evidenceScreen() {
        evidenceSearch.setPromptText("Search description, reference, case, or location");
        Button search = new Button("Search");
        search.setOnAction(event -> refreshEvidence(search));
        Button clear = new Button("Clear filters");
        clear.setOnAction(event -> {
            evidenceSearch.clear();
            caseFilter.setValue(null);
            locationFilter.setValue(null);
            stateFilter.setValue(null);
            includeVoided.setSelected(false);
            refreshEvidence(clear);
        });
        caseFilter.setPromptText("All cases");
        locationFilter.setPromptText("All locations");
        stateFilter.setPromptText("All statuses");
        stateFilter.setItems(FXCollections.observableArrayList(EvidenceCustodyState.values()));
        stateFilter.setConverter(converter(CustodianCaseworkView::displayState));
        caseFilter.setOnAction(event -> applyEvidenceFilters());
        locationFilter.setOnAction(event -> applyEvidenceFilters());
        stateFilter.setOnAction(event -> applyEvidenceFilters());
        includeVoided.setOnAction(event -> refreshEvidence(null));

        evidenceResults.setPlaceholder(new Label("No evidence matches these filters."));
        evidenceResults.getSelectionModel().selectedItemProperty().addListener((
                observable, oldValue, newValue) -> showEvidenceDetails(newValue));
        copyReference.setOnAction(event ->
                copyReference(evidenceResults.getSelectionModel().getSelectedItem()));
        voidEvidence.setOnAction(event ->
                showVoidDialog(evidenceResults.getSelectionModel().getSelectedItem()));
        evidenceDetails.setWrapText(true);
        voidGuidance.setWrapText(true);

        HBox searchRow = row(evidenceSearch, search, clear);
        HBox filters = row(caseFilter, locationFilter, stateFilter, includeVoided);
        VBox details = new VBox(SPACING, new Label("Selected evidence"), evidenceDetails,
                voidGuidance, row(copyReference, voidEvidence));
        details.setPadding(new Insets(12));
        details.setStyle("-fx-background-color: #f2f4f7;");
        VBox content = new VBox(SPACING,
                new Label("Evidence search"),
                searchRow,
                filters,
                evidenceResults,
                details);
        content.setPadding(new Insets(16));
        VBox.setVgrow(evidenceResults, Priority.ALWAYS);
        return content;
    }

    private Parent locationScreen() {
        TextField name = new TextField();
        name.setPromptText("New storage location");
        Button add = new Button("Add location");
        add.setOnAction(event -> run(add, () -> controller.addStorageLocation(name.getText()),
                locationId -> {
                    name.clear();
                    refreshLocations();
                    showSuccess("Storage location added");
                }, null));
        locationResults.setPlaceholder(new Label("No storage locations have been added."));
        VBox content = new VBox(SPACING,
                new Label("Storage locations"),
                new Label("Locations become available during evidence registration."),
                row(name, add),
                locationResults);
        content.setPadding(new Insets(16));
        VBox.setVgrow(locationResults, Priority.ALWAYS);
        return content;
    }

    private void configureControls() {
        caseResults.setCellFactory(list -> textCell(CaseworkViews.Case::title));
        currentAssignments.setCellFactory(list -> textCell(
                item -> item.displayName() + " (" + item.username() + ")"));
        locationResults.setCellFactory(list -> textCell(CaseworkViews.StorageLocation::name));
        caseResults.getSelectionModel().selectedItemProperty().addListener((
                observable, oldValue, newValue) -> showCaseContext(newValue));
        currentAssignments.getSelectionModel().selectedItemProperty().addListener((
                observable, oldValue, newValue) -> updateCaseActions());
        copyReference.setDisable(true);
        voidEvidence.setDisable(true);
        updateCaseActions();
    }

    private void showCreateCaseDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Create case");
        dialog.setHeaderText("Create a case with its initial Investigator");
        ButtonType create = new ButtonType("Create case", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);
        TextField title = new TextField();
        title.setPromptText("Case title");
        ComboBox<CaseworkViews.Investigator> investigator = investigatorComboBox();
        investigator.setItems(FXCollections.observableArrayList(investigators));
        if (!investigators.isEmpty()) {
            investigator.getSelectionModel().selectFirst();
        }
        GridPane form = form();
        form.addRow(0, new Label("Title"), title);
        form.addRow(1, new Label("Initial Investigator"), investigator);
        dialog.getDialogPane().setContent(form);
        dialog.showAndWait().filter(create::equals).ifPresent(ignored ->
                run(null, () -> controller.createCase(title.getText(), investigator.getValue()),
                        caseId -> {
                            refreshCases();
                            showSuccess("Case created");
                        }, null));
    }

    private void showRegistrationDialog(CaseworkViews.Case selectedCase) {
        if (selectedCase == null) {
            showError("Select a case before registering evidence");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Register evidence");
        dialog.setHeaderText("Register evidence to " + selectedCase.title());
        ButtonType register = new ButtonType(
                "Review registration", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(register, ButtonType.CANCEL);
        TextField description = new TextField();
        description.setPromptText("Short evidence description");
        ComboBox<CaseworkViews.StorageLocation> location = locationComboBox();
        location.setItems(FXCollections.observableArrayList(locations));
        if (!locations.isEmpty()) {
            location.getSelectionModel().selectFirst();
        }
        Label duplicateWarning = new Label();
        duplicateWarning.setWrapText(true);
        duplicateWarning.setStyle("-fx-text-fill: #9a6700;");
        Runnable updateWarning = () -> duplicateWarning.setText(possibleDuplicate(
                evidence, selectedCase, description.getText(), location.getValue())
                        ? "Possible duplicate: the same description and location already exist "
                                + "in this case. You may continue if these are separate items."
                        : "Existing evidence remains visible in the selected case behind this dialog.");
        description.textProperty().addListener((observable, oldValue, newValue) ->
                updateWarning.run());
        location.setOnAction(event -> updateWarning.run());
        updateWarning.run();
        GridPane form = form();
        form.addRow(0, new Label("Case"), new Label(selectedCase.title()));
        form.addRow(1, new Label("Description"), description);
        form.addRow(2, new Label("Location"), location);
        form.add(duplicateWarning, 1, 3);
        dialog.getDialogPane().setContent(form);
        dialog.showAndWait().filter(register::equals).ifPresent(ignored -> {
            CaseworkViews.StorageLocation selectedLocation = location.getValue();
            String locationName = selectedLocation == null ? "Not selected" : selectedLocation.name();
            String summary = "Case: " + selectedCase.title()
                    + "\nDescription: " + description.getText().strip()
                    + "\nLocation: " + locationName;
            confirm("Confirm evidence registration", summary, () ->
                    run(null, () -> controller.registerEvidence(
                            selectedCase, description.getText(), selectedLocation), evidenceId -> {
                                refreshEvidence(null);
                                workflowView.showHistoryForCase(selectedCase);
                                showSuccess("Evidence registered: EV-" + evidenceId);
                            }, null));
        });
    }

    private void showVoidDialog(CaseworkViews.Evidence selectedEvidence) {
        if (selectedEvidence == null) {
            showError("Select an evidence item");
            return;
        }
        TextInputDialog reasonDialog = new TextInputDialog();
        reasonDialog.setTitle("Void erroneous registration");
        reasonDialog.setHeaderText("The evidence record and history will be retained");
        reasonDialog.setContentText("Reason");
        reasonDialog.showAndWait()
                .map(String::strip)
                .filter(reason -> !reason.isEmpty())
                .ifPresent(reason -> confirm(
                        "Confirm evidence void",
                        "Void " + selectedEvidence.publicReference()
                                + "? This cannot be undone and will be recorded in history.", () ->
                            run(voidEvidence, () ->
                                    controller.voidEvidence(selectedEvidence, reason),
                                    ignored -> {
                                        refreshEvidence(null);
                                        workflowView.showHistoryForCase(caseFor(selectedEvidence));
                                        showSuccess("Evidence registration voided");
                                }, null)));
    }

    private void assignInvestigator(Button button) {
        CaseworkViews.Case selectedCase = selectedCase();
        run(button, () -> controller.addAssignment(
                selectedCase, assignmentInvestigator.getValue()), ignored -> {
                    refreshAssignments(selectedCase, button);
                    workflowView.showHistoryForCase(selectedCase);
                    showSuccess("Investigator assigned");
                }, null);
    }

    private void removeAssignment(Button button) {
        CaseworkViews.Case selectedCase = selectedCase();
        CaseworkViews.Investigator selectedInvestigator =
                currentAssignments.getSelectionModel().getSelectedItem();
        if (selectedInvestigator == null) {
            showError("Select an assigned Investigator");
            return;
        }
        confirm("Remove investigator",
                "Remove " + selectedInvestigator.displayName() + " from this case?", () ->
                    run(button, () -> controller.removeAssignment(
                            selectedCase, selectedInvestigator), ignored -> {
                            refreshAssignments(selectedCase, button);
                            workflowView.showHistoryForCase(selectedCase);
                            showSuccess("Investigator removed from case");
                        }, null));
    }

    private void showCaseContext(CaseworkViews.Case selectedCase) {
        selectedCaseTitle.setText(selectedCase == null ? "Select a case" : selectedCase.title());
        workflowView.showHistoryForCase(selectedCase);
        if (selectedCase == null) {
            currentAssignments.getItems().clear();
        } else {
            refreshAssignments(selectedCase, null);
        }
        applyEvidenceFilters();
        updateCaseActions();
    }

    private void showEvidenceDetails(CaseworkViews.Evidence selectedEvidence) {
        if (selectedEvidence == null) {
            evidenceDetails.setText("Select evidence to view its reference.");
            copyReference.setDisable(true);
            voidEvidence.setDisable(true);
            return;
        }
        evidenceDetails.setText("Reference: " + selectedEvidence.publicReference()
                + "\nRegistered: " + TIME_FORMAT.format(selectedEvidence.registeredAt()));
        copyReference.setDisable(false);
        voidEvidence.setDisable(!canOfferVoid(selectedEvidence));
        voidGuidance.setText(canOfferVoid(selectedEvidence)
                ? "A reason and final warning are required. The service will also verify that no "
                        + "checkout request has ever existed."
                : "This item cannot be voided because it is not currently in storage.");
    }

    private void refreshReferenceData() {
        refreshCases();
        refreshInvestigators();
        refreshLocations();
        refreshEvidence(null);
    }

    private void refreshCases() {
        run(null, () -> controller.searchCases(""), this::showCases, "Cases refreshed");
    }

    private void showCases(List<CaseworkViews.Case> loadedCases) {
        CaseworkViews.Case previous = selectedCase();
        cases = List.copyOf(loadedCases);
        caseResults.setItems(FXCollections.observableArrayList(cases));
        replaceItems(caseFilter, cases);
        if (previous != null) {
            cases.stream().filter(item -> item.caseId().equals(previous.caseId()))
                    .findFirst().ifPresent(caseResults.getSelectionModel()::select);
        }
        if (caseResults.getSelectionModel().getSelectedItem() == null && !cases.isEmpty()) {
            caseResults.getSelectionModel().selectFirst();
        }
    }

    private void refreshInvestigators() {
        run(null, controller::listInvestigators, loadedInvestigators -> {
            investigators = List.copyOf(loadedInvestigators);
            replaceItems(assignmentInvestigator, investigators);
        }, "Investigators refreshed");
    }

    private void refreshAssignments(CaseworkViews.Case selectedCase, Button button) {
        run(button, () -> controller.listAssignments(selectedCase), assignments -> {
            currentAssignments.setItems(FXCollections.observableArrayList(assignments));
            updateCaseActions();
        }, "Assignments refreshed");
    }

    private void refreshLocations() {
        run(null, controller::listStorageLocations, loadedLocations -> {
            locations = List.copyOf(loadedLocations);
            locationResults.setItems(FXCollections.observableArrayList(locations));
            replaceItems(locationFilter, locations);
        }, "Locations refreshed");
    }

    private void refreshEvidence(Button button) {
        run(button, () -> controller.searchEvidence(
                evidenceSearch.getText(), includeVoided.isSelected()), loadedEvidence -> {
                    evidence = List.copyOf(loadedEvidence);
                    applyEvidenceFilters();
                }, "Evidence refreshed");
    }

    private void applyEvidenceFilters() {
        List<CaseworkViews.Evidence> filtered = filterEvidence(
                evidence, caseFilter.getValue(), locationFilter.getValue(), stateFilter.getValue());
        evidenceResults.setItems(FXCollections.observableArrayList(filtered));
        CaseworkViews.Case selectedCase = selectedCase();
        List<CaseworkViews.Evidence> selectedCaseEvidence = selectedCase == null
                ? List.of()
                : evidence.stream()
                        .filter(item -> item.caseId().equals(selectedCase.caseId()))
                        .toList();
        caseEvidence.setItems(FXCollections.observableArrayList(selectedCaseEvidence));
    }

    private CaseworkViews.Case selectedCase() {
        return caseResults.getSelectionModel().getSelectedItem();
    }

    private CaseworkViews.Case caseFor(CaseworkViews.Evidence selectedEvidence) {
        return cases.stream()
                .filter(item -> item.caseId().equals(selectedEvidence.caseId()))
                .findFirst()
                .orElse(null);
    }

    private void updateCaseActions() {
        boolean noCase = selectedCase() == null;
        assignmentInvestigator.setDisable(noCase);
        registerEvidence.setDisable(noCase);
        removeAssignment.setDisable(noCase
                || currentAssignments.getSelectionModel().getSelectedItem() == null);
    }

    private void copyReference(CaseworkViews.Evidence selectedEvidence) {
        if (selectedEvidence == null) {
            showError("Select an evidence item");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(selectedEvidence.publicReference());
        Clipboard.getSystemClipboard().setContent(content);
        showSuccess("Evidence reference copied");
    }

    private <T> void run(
            Button initiatingButton,
            Supplier<CaseworkController.Result<T>> operation,
            Consumer<T> onSuccess,
            String successMessage) {
        if (initiatingButton != null) {
            initiatingButton.setDisable(true);
        }
        databaseExecutor.execute(() -> {
            CaseworkController.Result<T> result;
            try {
                result = operation.get();
            } catch (RuntimeException exception) {
                LOGGER.log(Level.SEVERE, "Unexpected casework screen failure", exception);
                Platform.runLater(() -> {
                    restoreButton(initiatingButton);
                    showError("The operation could not be completed. Please try again.");
                });
                return;
            }
            Platform.runLater(() -> {
                restoreButton(initiatingButton);
                if (!result.successful()) {
                    showError(result.message());
                    return;
                }
                onSuccess.accept(result.value());
                if (successMessage != null) {
                    showSuccess(successMessage);
                }
            });
        });
    }

    private void restoreButton(Button button) {
        if (button != null) {
            button.setDisable(false);
        }
        updateCaseActions();
        showEvidenceDetails(evidenceResults.getSelectionModel().getSelectedItem());
    }

    private void showStatus(String message) {
        status.setText(message);
        status.setStyle("-fx-background-color: #e8eef6; -fx-padding: 8;");
    }

    private void showSuccess(String message) {
        status.setText(message);
        status.setStyle("-fx-background-color: #e7f4ea; -fx-text-fill: #145c2e; "
                + "-fx-padding: 8;");
    }

    private void showError(String message) {
        status.setText(message);
        status.setStyle("-fx-background-color: #fde8e8; -fx-text-fill: #8b1a1a; "
                + "-fx-padding: 8;");
    }

    static List<String> evidenceColumns() {
        return EVIDENCE_COLUMNS;
    }

    static boolean possibleDuplicate(
            List<CaseworkViews.Evidence> availableEvidence,
            CaseworkViews.Case selectedCase,
            String description,
            CaseworkViews.StorageLocation selectedLocation) {
        if (selectedCase == null || selectedLocation == null
                || description == null || description.isBlank()) {
            return false;
        }
        String normalized = description.strip();
        return availableEvidence.stream().anyMatch(item ->
                item.caseId().equals(selectedCase.caseId())
                        && item.storageLocationId().equals(
                                selectedLocation.storageLocationId())
                        && item.description().equalsIgnoreCase(normalized)
                        && item.custodyState() != EvidenceCustodyState.VOIDED);
    }

    static List<CaseworkViews.Evidence> filterEvidence(
            List<CaseworkViews.Evidence> availableEvidence,
            CaseworkViews.Case selectedCase,
            CaseworkViews.StorageLocation selectedLocation,
            EvidenceCustodyState selectedState) {
        return availableEvidence.stream()
                .filter(item -> selectedCase == null
                        || item.caseId().equals(selectedCase.caseId()))
                .filter(item -> selectedLocation == null
                        || item.storageLocationId().equals(
                                selectedLocation.storageLocationId()))
                .filter(item -> selectedState == null || item.custodyState() == selectedState)
                .toList();
    }

    static boolean canOfferVoid(CaseworkViews.Evidence selectedEvidence) {
        return selectedEvidence != null
                && selectedEvidence.custodyState() == EvidenceCustodyState.IN_STORAGE;
    }

    private static String displayState(EvidenceCustodyState state) {
        String[] words = state.name().toLowerCase().split("_");
        StringBuilder value = new StringBuilder();
        for (String word : words) {
            if (!value.isEmpty()) {
                value.append(' ');
            }
            value.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return value.toString();
    }

    private static TableView<CaseworkViews.Evidence> evidenceTable() {
        TableView<CaseworkViews.Evidence> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().add(evidenceColumn("Description", CaseworkViews.Evidence::description));
        table.getColumns().add(evidenceColumn(
                "Location", CaseworkViews.Evidence::storageLocationName));
        table.getColumns().add(evidenceColumn("Case", CaseworkViews.Evidence::caseTitle));
        table.getColumns().add(evidenceColumn(
                "Status", item -> displayState(item.custodyState())));
        table.setRowFactory(ignored -> new TableRow<>() {
            @Override
            protected void updateItem(CaseworkViews.Evidence item, boolean empty) {
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
        return table;
    }

    private static TableColumn<CaseworkViews.Evidence, String> evidenceColumn(
            String title, Function<CaseworkViews.Evidence, String> value) {
        TableColumn<CaseworkViews.Evidence, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private static GridPane form() {
        GridPane form = new GridPane();
        form.setHgap(SPACING);
        form.setVgap(SPACING);
        form.setPadding(new Insets(10));
        return form;
    }

    private static Tab fixedTab(String title, Parent content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
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

    private static ComboBox<CaseworkViews.Case> caseComboBox() {
        ComboBox<CaseworkViews.Case> comboBox = new ComboBox<>();
        comboBox.setConverter(converter(CaseworkViews.Case::title));
        comboBox.setMaxWidth(Double.MAX_VALUE);
        return comboBox;
    }

    private static ComboBox<CaseworkViews.Investigator> investigatorComboBox() {
        ComboBox<CaseworkViews.Investigator> comboBox = new ComboBox<>();
        comboBox.setConverter(converter(item ->
                item.displayName() + " (" + item.username() + ")"));
        comboBox.setMaxWidth(Double.MAX_VALUE);
        return comboBox;
    }

    private static ComboBox<CaseworkViews.StorageLocation> locationComboBox() {
        ComboBox<CaseworkViews.StorageLocation> comboBox = new ComboBox<>();
        comboBox.setConverter(converter(CaseworkViews.StorageLocation::name));
        comboBox.setMaxWidth(Double.MAX_VALUE);
        return comboBox;
    }

    private static <T> StringConverter<T> converter(Function<T, String> text) {
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

    private static <T> ListCell<T> textCell(Function<T, String> text) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : text.apply(item));
            }
        };
    }

    private static <T> void replaceItems(ComboBox<T> comboBox, List<T> items) {
        T previous = comboBox.getValue();
        comboBox.setItems(FXCollections.observableArrayList(items));
        if (previous != null && items.contains(previous)) {
            comboBox.setValue(previous);
        }
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
}
