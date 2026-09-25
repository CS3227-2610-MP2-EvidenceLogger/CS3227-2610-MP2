package evidencelogger.ui.custodian;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import evidencelogger.service.dto.CaseworkViews;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Code-built Custodian workspace for cases, assignments, locations, and evidence. */
public final class CustodianCaseworkView {
    private static final double SPACING = 10;
    private static final Logger LOGGER = Logger.getLogger(
            CustodianCaseworkView.class.getName());

    private final CaseworkController controller;
    private final Executor databaseExecutor;
    private final BorderPane root;
    private final Label status;
    private final ComboBox<CaseworkViews.Investigator> initialInvestigator;
    private final ListView<CaseworkViews.Case> caseResults;
    private final ComboBox<CaseworkViews.Case> assignmentCase;
    private final ComboBox<CaseworkViews.Investigator> assignmentInvestigator;
    private final ListView<CaseworkViews.Investigator> currentAssignments;
    private final ListView<CaseworkViews.StorageLocation> locationResults;
    private final ComboBox<CaseworkViews.Case> evidenceCase;
    private final ComboBox<CaseworkViews.StorageLocation> evidenceLocation;
    private final ListView<CaseworkViews.Evidence> evidenceResults;

    /** Creates all A4 casework screens and starts loading their reference data. */
    public CustodianCaseworkView(
            CaseworkController controller, Executor databaseExecutor) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        status = new Label("Loading casework data...");
        initialInvestigator = investigatorComboBox();
        caseResults = new ListView<>();
        assignmentCase = caseComboBox();
        assignmentInvestigator = investigatorComboBox();
        currentAssignments = new ListView<>();
        locationResults = new ListView<>();
        evidenceCase = caseComboBox();
        evidenceLocation = locationComboBox();
        evidenceResults = new ListView<>();

        configureLists();
        TabPane tabs = new TabPane(
                fixedTab("Cases", caseScreen()),
                fixedTab("Assignments", assignmentScreen()),
                fixedTab("Locations", locationScreen()),
                fixedTab("Register evidence", registrationScreen()),
                fixedTab("Evidence search", evidenceSearchScreen()));
        root = new BorderPane(tabs);
        root.setBottom(status);
        BorderPane.setMargin(status, new Insets(SPACING));
        refreshReferenceData();
    }

    /** Returns the root node for role-specific navigation. */
    public Parent view() {
        return root;
    }

    private Parent caseScreen() {
        TextField search = new TextField();
        search.setPromptText("Case title");
        Button searchButton = new Button("Search");
        searchButton.setOnAction(event -> {
            String searchText = search.getText();
            run(searchButton, () -> controller.searchCases(searchText),
                    this::showCases, "Cases refreshed");
        });

        TextField title = new TextField();
        title.setPromptText("New case title");
        Button create = new Button("Create case");
        create.setOnAction(event -> {
            String enteredTitle = title.getText();
            CaseworkViews.Investigator selectedInvestigator = initialInvestigator.getValue();
            run(create, () -> controller.createCase(enteredTitle, selectedInvestigator), caseId -> {
                title.clear();
                showStatus("Case created: " + caseId);
                refreshCases();
            },
                null);
        });

        VBox content = screen(
                "Create cases with their required initial Investigator, then search all cases.",
                row(title, initialInvestigator, create),
                row(search, searchButton),
                caseResults);
        VBox.setVgrow(caseResults, Priority.ALWAYS);
        return content;
    }

    private Parent assignmentScreen() {
        Button load = new Button("Load assignments");
        load.setOnAction(event -> refreshAssignments(load));
        assignmentCase.setOnAction(event -> refreshAssignments(load));

        Button add = new Button("Assign");
        add.setOnAction(event -> {
            CaseworkViews.Case selectedCase = assignmentCase.getValue();
            CaseworkViews.Investigator selectedInvestigator =
                    assignmentInvestigator.getValue();
            run(add, () -> controller.addAssignment(
                    selectedCase, selectedInvestigator), ignored -> {
                    showStatus("Investigator assigned");
                    refreshAssignments(add);
                },
                null);
        });

        Button remove = new Button("Remove selected assignment");
        remove.setOnAction(event -> {
            CaseworkViews.Case selectedCase = assignmentCase.getValue();
            CaseworkViews.Investigator selectedInvestigator =
                    currentAssignments.getSelectionModel().getSelectedItem();
            run(remove, () -> controller.removeAssignment(
                    selectedCase, selectedInvestigator), ignored -> {
                    showStatus("Assignment removed");
                    refreshAssignments(remove);
                },
                null);
        });

        VBox content = screen(
                "Add or remove Investigators from an existing case.",
                row(assignmentCase, load),
                row(assignmentInvestigator, add),
                currentAssignments,
                remove);
        VBox.setVgrow(currentAssignments, Priority.ALWAYS);
        return content;
    }

    private Parent locationScreen() {
        TextField name = new TextField();
        name.setPromptText("New storage location");
        Button add = new Button("Add location");
        add.setOnAction(event -> {
            String enteredName = name.getText();
            run(add, () -> controller.addStorageLocation(enteredName), locationId -> {
                name.clear();
                showStatus("Storage location added: " + locationId);
                refreshLocations();
            },
                null);
        });
        VBox content = screen(
                "Maintain the locations available during evidence registration.",
                row(name, add),
                locationResults);
        VBox.setVgrow(locationResults, Priority.ALWAYS);
        return content;
    }

    private Parent registrationScreen() {
        TextField description = new TextField();
        description.setPromptText("Short evidence description");
        Button register = new Button("Register evidence");
        register.setOnAction(event -> {
            CaseworkViews.Case selectedCase = evidenceCase.getValue();
            String enteredDescription = description.getText();
            CaseworkViews.StorageLocation selectedLocation = evidenceLocation.getValue();
            run(register, () -> controller.registerEvidence(
                    selectedCase, enteredDescription, selectedLocation), evidenceId -> {
                    description.clear();
                    showStatus("Evidence registered: " + evidenceId);
                    refreshEvidence();
                },
                null);
        });

        GridPane form = new GridPane();
        form.setHgap(SPACING);
        form.setVgap(SPACING);
        form.addRow(0, new Label("Case"), evidenceCase);
        form.addRow(1, new Label("Description"), description);
        form.addRow(2, new Label("Location"), evidenceLocation);
        form.add(register, 1, 3);
        GridPane.setHgrow(description, Priority.ALWAYS);
        return screen(
                "Register one item using exactly a case, short description, and location.",
                form);
    }

    private Parent evidenceSearchScreen() {
        TextField search = new TextField();
        search.setPromptText("Reference, description, case, or location");
        Button searchButton = new Button("Search");
        searchButton.setOnAction(event -> {
            String searchText = search.getText();
            run(searchButton, () -> controller.searchEvidence(searchText),
                    this::showEvidence, "Evidence refreshed");
        });
        VBox content = screen(
                "Search registered evidence and review its current custody state.",
                row(search, searchButton),
                evidenceResults);
        VBox.setVgrow(evidenceResults, Priority.ALWAYS);
        return content;
    }

    private void configureLists() {
        caseResults.setCellFactory(list -> textCell(CaseworkViews.Case::title));
        currentAssignments.setCellFactory(list -> textCell(
                item -> item.displayName() + " (" + item.username() + ")"));
        locationResults.setCellFactory(list -> textCell(CaseworkViews.StorageLocation::name));
        evidenceResults.setCellFactory(list -> textCell(item -> String.format(
                "%s — %s | %s | %s | %s",
                item.publicReference(),
                item.description(),
                item.caseTitle(),
                item.storageLocationName(),
                item.custodyState())));
    }

    private void refreshReferenceData() {
        refreshCases();
        refreshInvestigators();
        refreshLocations();
        refreshEvidence();
    }

    private void refreshCases() {
        run(null, () -> controller.searchCases(""), this::showCases, "Cases refreshed");
    }

    private void showCases(List<CaseworkViews.Case> cases) {
        caseResults.setItems(FXCollections.observableArrayList(cases));
        replaceItems(assignmentCase, cases);
        replaceItems(evidenceCase, cases);
    }

    private void refreshInvestigators() {
        run(null, controller::listInvestigators, investigators -> {
            replaceItems(initialInvestigator, investigators);
            replaceItems(assignmentInvestigator, investigators);
        }, "Investigators refreshed");
    }

    private void refreshAssignments(Button initiatingButton) {
        CaseworkViews.Case selectedCase = assignmentCase.getValue();
        run(initiatingButton, () ->
                controller.listAssignments(selectedCase),
                assignments -> currentAssignments.setItems(
                        FXCollections.observableArrayList(assignments)),
                "Assignments refreshed");
    }

    private void refreshLocations() {
        run(null, controller::listStorageLocations, locations -> {
            locationResults.setItems(FXCollections.observableArrayList(locations));
            replaceItems(evidenceLocation, locations);
        }, "Locations refreshed");
    }

    private void refreshEvidence() {
        run(null, () -> controller.searchEvidence(""), this::showEvidence, "Evidence refreshed");
    }

    private void showEvidence(List<CaseworkViews.Evidence> evidence) {
        evidenceResults.setItems(FXCollections.observableArrayList(evidence));
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
                    if (initiatingButton != null) {
                        initiatingButton.setDisable(false);
                    }
                    showStatus("The operation could not be completed. Please try again.");
                });
                return;
            }
            Platform.runLater(() -> {
                if (initiatingButton != null) {
                    initiatingButton.setDisable(false);
                }
                if (!result.successful()) {
                    showStatus(result.message());
                    return;
                }
                onSuccess.accept(result.value());
                if (successMessage != null) {
                    showStatus(successMessage);
                }
            });
        });
    }

    private void showStatus(String message) {
        status.setText(message);
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
        T selected = comboBox.getValue();
        comboBox.setItems(FXCollections.observableArrayList(items));
        if (selected != null && items.contains(selected)) {
            comboBox.setValue(selected);
        } else if (!items.isEmpty()) {
            comboBox.getSelectionModel().selectFirst();
        }
    }
}
