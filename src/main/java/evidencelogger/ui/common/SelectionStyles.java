package evidencelogger.ui.common;

import java.net.URL;
import java.util.Objects;

import javafx.scene.Parent;

/** Applies the shared, focus-independent selection treatment to a role workspace. */
public final class SelectionStyles {
    private static final String STYLESHEET = "/evidencelogger/ui/selection.css";

    private SelectionStyles() {
    }

    /** Adds the shared row and selection stylesheet to the supplied workspace root. */
    public static void applyTo(Parent root) {
        Objects.requireNonNull(root, "root");
        URL stylesheet = Objects.requireNonNull(
                SelectionStyles.class.getResource(STYLESHEET),
                "Missing selection stylesheet");
        root.getStylesheets().add(stylesheet.toExternalForm());
    }
}
