package evidencelogger.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticLoggingTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesPersistentDiagnosticsBeneathTheApplicationLogDirectory() throws IOException {
        Path logs = temporaryDirectory.resolve("logs");

        DiagnosticLogging logging = DiagnosticLogging.start(logs);
        try {
            Logger.getLogger("evidencelogger.test").warning("persistent diagnostic marker");
        } finally {
            logging.close();
        }

        try (Stream<Path> files = Files.list(logs)) {
            assertTrue(files.anyMatch(this::containsDiagnosticMarker));
        }
    }

    private boolean containsDiagnosticMarker(Path file) {
        try {
            return Files.readString(file).contains("persistent diagnostic marker");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read diagnostic log", exception);
        }
    }
}
