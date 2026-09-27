package evidencelogger.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApplicationDataPathsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesWindowsLocalAppDataAndHomeFallback() {
        Path configured = temporaryDirectory.resolve("WindowsLocal");
        ApplicationDataPaths fromEnvironment = ApplicationDataPaths.resolve(
                "Windows 11", temporaryDirectory, Map.of(
                        "LOCALAPPDATA", configured.toString()));
        ApplicationDataPaths fromHome = ApplicationDataPaths.resolve(
                "Windows 11", temporaryDirectory, Map.of());

        assertPaths(fromEnvironment, configured);
        assertPaths(fromHome, temporaryDirectory.resolve("AppData").resolve("Local"));
    }

    @Test
    void resolvesMacOsApplicationSupport() {
        ApplicationDataPaths paths = ApplicationDataPaths.resolve(
                "Mac OS X", temporaryDirectory, Map.of());

        assertPaths(paths, temporaryDirectory.resolve("Library").resolve("Application Support"));
    }

    @Test
    void resolvesLinuxXdgDataHomeAndHomeFallback() {
        Path configured = temporaryDirectory.resolve("xdg-data");
        ApplicationDataPaths fromEnvironment = ApplicationDataPaths.resolve(
                "Linux", temporaryDirectory, Map.of("XDG_DATA_HOME", configured.toString()));
        ApplicationDataPaths fromHome = ApplicationDataPaths.resolve(
                "Linux", temporaryDirectory, Map.of());

        assertPaths(fromEnvironment, configured);
        assertPaths(fromHome, temporaryDirectory.resolve(".local").resolve("share"));
    }

    @Test
    void preparesApplicationAndLogDirectories() {
        ApplicationDataPaths paths = ApplicationDataPaths.resolve(
                "Linux", temporaryDirectory, Map.of());

        paths.prepare();

        assertTrue(Files.isDirectory(paths.directory()));
        assertTrue(Files.isDirectory(paths.logs()));
    }

    private static void assertPaths(ApplicationDataPaths paths, Path baseDirectory) {
        Path expectedDirectory = baseDirectory.resolve("EvidenceLogger")
                .toAbsolutePath()
                .normalize();
        assertEquals(expectedDirectory, paths.directory());
        assertEquals(expectedDirectory.resolve("evidence-logger.db"), paths.database());
        assertEquals(expectedDirectory.resolve("logs"), paths.logs());
    }
}
