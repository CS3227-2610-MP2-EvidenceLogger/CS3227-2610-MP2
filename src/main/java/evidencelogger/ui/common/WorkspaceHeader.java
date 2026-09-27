package evidencelogger.ui.common;

import java.util.Objects;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/** Shared role-workspace header containing identity and sign-out controls. */
public final class WorkspaceHeader {
    private static final String BACKGROUND_STYLE = "-fx-background-color: #102b4c;";
    private static final String TEXT_STYLE = "-fx-text-fill: white;";
    private static final double HEADING_FONT_SIZE = 24;

    private WorkspaceHeader() {
    }

    /** Describes the user-visible header content and its sign-out action. */
    public record Configuration(String workspaceName, String displayName, Runnable signOut) {
        /** Creates validated header configuration. */
        public Configuration {
            Objects.requireNonNull(workspaceName, "workspaceName");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(signOut, "signOut");
        }

        /** Returns the application and role-workspace title shown in the header. */
        public String title() {
            return ApplicationShell.APPLICATION_NAME + "   |   " + workspaceName;
        }
    }

    /** Builds the shared dark-blue header from validated presentation data. */
    public static Parent create(Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        Label title = new Label(configuration.title());
        Label user = new Label(configuration.displayName());
        Button signOut = new Button("Sign out");
        signOut.setOnAction(event -> configuration.signOut().run());

        HBox bar = new HBox(18, title, user, signOut);
        bar.setPadding(new Insets(16));
        bar.setMaxWidth(Double.MAX_VALUE);
        title.setStyle(TEXT_STYLE);
        title.setFont(Font.font("System", FontWeight.BOLD, HEADING_FONT_SIZE));
        user.setStyle(TEXT_STYLE);
        user.setFont(Font.font("System", FontWeight.BOLD, HEADING_FONT_SIZE));
        signOut.setFont(Font.font("System", FontWeight.BOLD, 20));
        HBox.setHgrow(title, Priority.ALWAYS);
        bar.setStyle(BACKGROUND_STYLE);
        return bar;
    }
}
