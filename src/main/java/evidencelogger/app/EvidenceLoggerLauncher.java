package evidencelogger.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import javafx.application.Application;

/**
 * Plain Java entry point used by Gradle now and by the release JAR later.
 */
public final class EvidenceLoggerLauncher {
    private EvidenceLoggerLauncher() {
    }

    public static void main(String[] args) {
        if (args.length == 1 && "--smoke-test".equals(args[0])) {
            runReleaseSmokeTest();
            return;
        }
        Application.launch(EvidenceLoggerApplication.class, args);
    }

    /** Starts the packaged composition with a real temporary SQLite database, then exits. */
    private static void runReleaseSmokeTest() {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("evidencelogger-release-smoke-");
            Path database = directory.resolve("smoke.db");
            try (ApplicationComposition composition =
                    ApplicationComposition.start(database, Clock.systemUTC())) {
                composition.authentication();
                System.out.println("EvidenceLogger release smoke test passed");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Release smoke test could not prepare temporary data",
                    exception);
        } finally {
            deleteSmokeData(directory);
        }
    }

    /** Removes only the explicitly named temporary files created by the release smoke test. */
    private static void deleteSmokeData(Path directory) {
        if (directory == null) {
            return;
        }
        try {
            Files.deleteIfExists(directory.resolve("smoke.db-wal"));
            Files.deleteIfExists(directory.resolve("smoke.db-shm"));
            Files.deleteIfExists(directory.resolve("smoke.db"));
            Files.deleteIfExists(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Release smoke test data could not be removed",
                    exception);
        }
    }
}
