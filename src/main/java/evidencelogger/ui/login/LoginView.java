package evidencelogger.ui.login;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import evidencelogger.service.auth.AuthenticatedSession;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/** Code-built sign-in screen for the application shell. */
public final class LoginView {
    private static final double SPACING = 10;
    private static final Logger LOGGER = Logger.getLogger(LoginView.class.getName());

    private final LoginController controller;
    private final Executor databaseExecutor;
    private final Consumer<AuthenticatedSession> onAuthenticated;
    private final VBox root;
    private final TextField username;
    private final PasswordField password;
    private final Button signIn;
    private final Label message;

    /** Creates the sign-in screen and its successful-authentication callback. */
    public LoginView(
            LoginController controller,
            Executor databaseExecutor,
            Consumer<AuthenticatedSession> onAuthenticated) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        this.onAuthenticated = Objects.requireNonNull(onAuthenticated, "onAuthenticated");

        Label title = new Label("EvidenceLogger");
        title.setFont(Font.font("System", FontWeight.BOLD, 28));
        Label subtitle = new Label("Sign in to manage evidence custody");
        username = new TextField();
        username.setPromptText("Username");
        password = new PasswordField();
        password.setPromptText("Password");
        signIn = new Button("Sign in");
        signIn.setDefaultButton(true);
        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setOnAction(event -> submit());
        message = new Label();
        message.setWrapText(true);

        VBox form = new VBox(SPACING, username, password, signIn, message);
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(280);

        root = new VBox(SPACING, title, subtitle, form);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(32));
        root.setMaxWidth(460);
    }

    /** Returns the root node displayed before protected role screens. */
    public Parent view() {
        return root;
    }

    /** Shows a navigation or authentication message without diagnostic details. */
    public void showMessage(String text) {
        message.setText(Objects.requireNonNull(text, "text"));
        message.setTextFill(text.isBlank() ? Color.BLACK : Color.FIREBRICK);
    }

    private void submit() {
        String suppliedUsername = username.getText();
        char[] suppliedPassword = password.getText().toCharArray();
        password.clear();
        signIn.setDisable(true);
        message.setText("Signing in...");
        message.setTextFill(Color.BLACK);

        databaseExecutor.execute(() -> {
            LoginController.Result result;
            try {
                result = controller.signIn(suppliedUsername, suppliedPassword);
            } catch (RuntimeException exception) {
                LOGGER.log(Level.SEVERE, "Unexpected sign-in screen failure", exception);
                Platform.runLater(() -> finishWithMessage(
                        "Sign-in could not be completed. Please try again."));
                return;
            }
            Platform.runLater(() -> {
                signIn.setDisable(false);
                if (!result.successful()) {
                    message.setText(result.message());
                    message.setTextFill(Color.FIREBRICK);
                    password.requestFocus();
                    return;
                }
                onAuthenticated.accept(result.session());
            });
        });
    }

    private void finishWithMessage(String text) {
        signIn.setDisable(false);
        message.setText(text);
        message.setTextFill(Color.FIREBRICK);
        password.requestFocus();
    }
}
