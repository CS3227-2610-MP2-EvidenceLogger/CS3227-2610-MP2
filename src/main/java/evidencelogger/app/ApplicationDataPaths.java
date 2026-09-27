package evidencelogger.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import evidencelogger.service.ServiceException;

/** Central platform-aware locations for mutable application data. */
public record ApplicationDataPaths(Path directory, Path database, Path logs) {
    /** Validates and normalizes all application data paths. */
    public ApplicationDataPaths {
        directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        database = Objects.requireNonNull(database, "database").toAbsolutePath().normalize();
        logs = Objects.requireNonNull(logs, "logs").toAbsolutePath().normalize();
    }

    /** Resolves platform-appropriate per-user data locations. */
    public static ApplicationDataPaths resolveDefault() {
        return resolve(
                System.getProperty("os.name", ""),
                Path.of(System.getProperty("user.home")),
                System.getenv());
    }

    /** Resolves paths from explicit platform inputs without mutating process-global state. */
    static ApplicationDataPaths resolve(
            String operatingSystem, Path home, Map<String, String> environment) {
        String normalizedOperatingSystem = Objects.requireNonNull(
                operatingSystem, "operatingSystem").toLowerCase(Locale.ROOT);
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(environment, "environment");
        Path baseDirectory;
        if (normalizedOperatingSystem.contains("win")) {
            String localAppData = environment.get("LOCALAPPDATA");
            baseDirectory = localAppData == null || localAppData.isBlank()
                    ? home.resolve("AppData").resolve("Local")
                    : Path.of(localAppData);
        } else if (normalizedOperatingSystem.contains("mac")) {
            baseDirectory = home.resolve("Library").resolve("Application Support");
        } else {
            String dataHome = environment.get("XDG_DATA_HOME");
            baseDirectory = dataHome == null || dataHome.isBlank()
                    ? home.resolve(".local").resolve("share")
                    : Path.of(dataHome);
        }
        Path directory = baseDirectory.resolve("EvidenceLogger");
        return new ApplicationDataPaths(
                directory,
                directory.resolve("evidence-logger.db"),
                directory.resolve("logs"));
    }

    /** Creates the application data and log directories before startup. */
    public void prepare() {
        try {
            Files.createDirectories(directory);
            Files.createDirectories(logs);
        } catch (IOException exception) {
            throw new ServiceException.StorageFailure(
                    "The application data directory could not be prepared", exception);
        }
    }
}
