package evidencelogger.app;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import evidencelogger.domain.Role;
import evidencelogger.infrastructure.logging.DiagnosticLogging;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.AuthenticationService;
import evidencelogger.ui.common.ApplicationShell;
import evidencelogger.ui.custodian.CaseworkController;
import evidencelogger.ui.custodian.CustodianCaseworkView;
import evidencelogger.ui.investigator.InvestigatorController;
import evidencelogger.ui.investigator.InvestigatorWorkspaceView;
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
        AuthenticatedRoleRouter router = new AuthenticatedRoleRouter(
                composition.authentication(),
                session -> showCustodianWorkspace(shell),
                session -> showInvestigatorWorkspace(shell, session),
                messageText -> showLogin(shell, messageText));
        LoginView login = new LoginView(
                controller,
                databaseExecutor,
                router);
        login.showMessage(message);
        shell.showContent(login.view());
    }

    private void showCustodianWorkspace(ApplicationShell shell) {
        CaseworkController controller = new CaseworkController(
                composition.caseworkCommands(), composition.caseworkQueries());
        shell.showContent(new CustodianCaseworkView(
                controller, databaseExecutor).view());
    }

    private void showInvestigatorWorkspace(ApplicationShell shell, AuthenticatedSession session) {
        InvestigatorController controller = new InvestigatorController(
                composition.caseworkQueries(),
                composition.checkoutQueries(),
                composition.checkoutCommands(),
                composition.historyQueries());
        shell.showContent(new InvestigatorWorkspaceView(
                controller, databaseExecutor, session.displayName(), () -> {
                    composition.authentication().signOut();
                    showLogin(shell, "");
                }).view());
    }

    /** Routes authenticated sessions without making JavaFX navigation an authorization boundary. */
    static final class AuthenticatedRoleRouter implements Consumer<AuthenticatedSession> {
        private final AuthenticationService authentication;
        private final Consumer<AuthenticatedSession> showCustodian;
        private final Consumer<AuthenticatedSession> showInvestigator;
        private final Consumer<String> showLogin;

        AuthenticatedRoleRouter(
                AuthenticationService authentication,
                Consumer<AuthenticatedSession> showCustodian,
                Consumer<AuthenticatedSession> showInvestigator,
                Consumer<String> showLogin) {
            this.authentication = Objects.requireNonNull(
                    authentication, "authentication");
            this.showCustodian = Objects.requireNonNull(
                    showCustodian, "showCustodian");
            this.showInvestigator = Objects.requireNonNull(
                    showInvestigator, "showInvestigator");
            this.showLogin = Objects.requireNonNull(showLogin, "showLogin");
        }

        @Override
        public void accept(AuthenticatedSession session) {
            Objects.requireNonNull(session, "session");
            if (session.role() == Role.EVIDENCE_CUSTODIAN) {
                showCustodian.accept(session);
                return;
            }
            if (session.role() == Role.INVESTIGATOR) {
                showInvestigator.accept(session);
                return;
            }
            authentication.signOut();
            showLogin.accept("The signed-in role is not available.");
        }
    }
}
