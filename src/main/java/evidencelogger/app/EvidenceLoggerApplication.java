package evidencelogger.app;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import evidencelogger.domain.Role;
import evidencelogger.infrastructure.logging.DiagnosticLogging;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.ui.common.ApplicationShell;
import evidencelogger.ui.custodian.CaseworkController;
import evidencelogger.ui.custodian.CustodianCaseworkView;
import evidencelogger.ui.login.LoginController;
import evidencelogger.ui.login.LoginView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Owns the JavaFX lifecycle, application object graph, and available role routing.
 */
public final class EvidenceLoggerApplication extends Application {
    private static final double INITIAL_WIDTH = 960;
    private static final double INITIAL_HEIGHT = 640;
    private static final Logger LOGGER = Logger.getLogger(EvidenceLoggerApplication.class.getName());

    private ExecutorService databaseExecutor;
    private ApplicationComposition composition;
    private DiagnosticLogging diagnosticLogging;

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
            diagnosticLogging = DiagnosticLogging.start(paths.logs());
            composition = ApplicationComposition.start(paths.database(), Clock.systemUTC());
            databaseExecutor = createDatabaseExecutor();
            showLogin(shell, "");
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
        if (diagnosticLogging != null) {
            diagnosticLogging.close();
            diagnosticLogging = null;
        }
    }

    private static ExecutorService createDatabaseExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "evidencelogger-database");
            thread.setDaemon(true);
            return thread;
        });
    }

    private void showLogin(ApplicationShell shell, String message) {
        LoginController controller = new LoginController(composition.authentication());
        LoginView login = new LoginView(
                controller,
                databaseExecutor,
                session -> showAuthenticatedWorkspace(shell, controller, session));
        login.showMessage(message);
        shell.showContent(login.view());
    }

    private void showAuthenticatedWorkspace(
            ApplicationShell shell,
            LoginController loginController,
            AuthenticatedSession session) {
        if (session.role() == Role.EVIDENCE_CUSTODIAN) {
            CaseworkController controller = new CaseworkController(
                    composition.caseworkCommands(), composition.caseworkQueries());
            shell.showContent(new CustodianCaseworkView(
                    controller, databaseExecutor).view());
            return;
        }

        loginController.signOut();
        showLogin(shell, "Investigator workspace is not available in this build.");
    }
}
