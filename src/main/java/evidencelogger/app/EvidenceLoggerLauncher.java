package evidencelogger.app;

import javafx.application.Application;

/**
 * Plain Java entry point used by Gradle now and by the release JAR later.
 */
public final class EvidenceLoggerLauncher {
    private EvidenceLoggerLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(EvidenceLoggerApplication.class, args);
    }
}
