package evidencelogger.app;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import evidencelogger.ui.common.ApplicationShell;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Owns the JavaFX lifecycle and, as features are added, the application object
 * graph.
 */
public final class EvidenceLoggerApplication extends Application {
    private static final double INITIAL_WIDTH = 960;
    private static final double INITIAL_HEIGHT = 640;
    private static final Logger LOGGER = Logger.getLogger(EvidenceLoggerApplication.class.getName());

    private ExecutorService databaseExecutor;
    private ApplicationComposition composition;

    @Override
    public void start(Stage primaryStage) {
        ApplicationShell shell = new ApplicationShell();
        primaryStage.setTitle(ApplicationShell.APPLICATION_NAME);
        primaryStage.setMinWidth(720);
        primaryStage.setMinHeight(480);
        primaryStage.setScene(new Scene(shell.view(), INITIAL_WIDTH, INITIAL_HEIGHT));

        try {
            ApplicationDataPaths paths = ApplicationDataPaths.resolveDefault();
            paths.prepare();
            composition = ApplicationComposition.start(paths.database(), Clock.systemUTC());
            databaseExecutor = createDatabaseExecutor();
            shell.showStatus("Database ready; sign-in services available");
        } catch (RuntimeException exception) {
            LOGGER.log(Level.SEVERE, "Application startup failed", exception);
            shell.showStatus("Startup failed. The local database could not be prepared.");
        }
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (composition != null) {
            composition.close();
            composition = null;
        }
        if (databaseExecutor != null) {
            databaseExecutor.shutdown();
            try {
                if (!databaseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    databaseExecutor.shutdownNow();
                }
            } catch (InterruptedException exception) {
                databaseExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            databaseExecutor = null;
        }
    }

    private static ExecutorService createDatabaseExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "evidencelogger-database");
            thread.setDaemon(true);
            return thread;
        });
    }
}
