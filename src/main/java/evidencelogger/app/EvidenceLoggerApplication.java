package evidencelogger.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private ExecutorService databaseExecutor;

    @Override
    public void start(Stage primaryStage) {
        databaseExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "evidencelogger-database");
            thread.setDaemon(true);
            return thread;
        });

        ApplicationShell shell = new ApplicationShell();
        primaryStage.setTitle(ApplicationShell.APPLICATION_NAME);
        primaryStage.setMinWidth(720);
        primaryStage.setMinHeight(480);
        primaryStage.setScene(new Scene(shell.view(), INITIAL_WIDTH, INITIAL_HEIGHT));
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (databaseExecutor != null) {
            databaseExecutor.shutdownNow();
        }
    }
}
