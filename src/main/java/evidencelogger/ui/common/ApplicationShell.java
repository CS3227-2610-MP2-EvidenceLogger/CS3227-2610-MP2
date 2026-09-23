package evidencelogger.ui.common;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Code-built top-level shell. Role navigation will replace the center content
 * after authentication is implemented.
 */
public final class ApplicationShell {
    public static final String APPLICATION_NAME = "EvidenceLogger";

    private final BorderPane root;

    public ApplicationShell() {
        Label title = new Label(APPLICATION_NAME);
        title.setFont(Font.font("System", FontWeight.BOLD, 28));

        Label subtitle = new Label("Offline evidence custody management");
        Label status = new Label("Application shell ready");

        VBox welcome = new VBox(10, title, subtitle, status);
        welcome.setAlignment(Pos.CENTER);
        welcome.setPadding(new Insets(32));

        root = new BorderPane(welcome);
    }

    public Parent view() {
        return root;
    }
}
