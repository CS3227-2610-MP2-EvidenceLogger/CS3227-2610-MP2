package evidencelogger.infrastructure.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Configures and owns the application's size-bounded rotating diagnostic log. */
public final class DiagnosticLogging implements AutoCloseable {
    private static final int LOG_SIZE_LIMIT_BYTES = 1_048_576;
    private static final int LOG_FILE_COUNT = 5;
    private static final Logger LOGGER = Logger.getLogger(DiagnosticLogging.class.getName());

    private final Logger rootLogger;
    private final Handler fileHandler;

    private DiagnosticLogging(Logger rootLogger, Handler fileHandler) {
        this.rootLogger = rootLogger;
        this.fileHandler = fileHandler;
    }

    /**
     * Adds rotating file diagnostics beneath the supplied directory.
     * Falls back to the existing console handlers if the file handler cannot be created.
     */
    public static DiagnosticLogging start(Path logDirectory) {
        Objects.requireNonNull(logDirectory, "logDirectory");
        Logger rootLogger = Logger.getLogger("");
        try {
            Files.createDirectories(logDirectory);
            String pattern = logDirectory.resolve("evidencelogger-%g.log").toString();
            FileHandler handler = new FileHandler(
                    pattern, LOG_SIZE_LIMIT_BYTES, LOG_FILE_COUNT, true);
            handler.setFormatter(new SimpleFormatter());
            handler.setLevel(Level.ALL);
            rootLogger.addHandler(handler);
            return new DiagnosticLogging(rootLogger, handler);
        } catch (IOException | SecurityException exception) {
            LOGGER.log(Level.WARNING,
                    "File diagnostics are unavailable; continuing with console logging",
                    exception);
            return new DiagnosticLogging(rootLogger, null);
        }
    }

    /** Removes and closes the application-owned file handler. */
    @Override
    public void close() {
        if (fileHandler != null) {
            rootLogger.removeHandler(fileHandler);
            fileHandler.close();
        }
    }
}
