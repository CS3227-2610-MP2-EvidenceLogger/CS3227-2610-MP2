package evidencelogger.ui.common;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class SelectionStylesTest {
    @Test
    void selectedTableAndListRowsStayBlueWithWhiteTextWhenFocusMoves() throws IOException {
        String css = stylesheet();

        assertTrue(css.contains(".table-row-cell:filled:selected"));
        assertTrue(css.contains(".list-cell:filled:selected"));
        assertTrue(css.contains("-fx-background-color: #2f6fad"));
        assertTrue(css.contains("-fx-text-fill: white"));
    }

    @Test
    void alternatingRowsUseTheSharedStylesheet() throws IOException {
        String css = stylesheet();

        assertTrue(css.contains(".table-row-cell:filled:even"));
        assertTrue(css.contains(".table-row-cell:filled:odd"));
        assertTrue(css.contains("-fx-background-color: #f2f4f7"));
    }

    @Test
    void comboBoxesKeepTheirValueNeutralAndHighlightOnlyHoveredPopupItems()
            throws IOException {
        String css = stylesheet();

        assertTrue(css.contains(".combo-box > .list-cell"));
        assertTrue(css.contains(".combo-box-popup .list-cell:filled:selected"));
        assertTrue(css.contains(".combo-box-popup .list-cell:filled:hover"));
        assertTrue(css.contains(".combo-box-popup .list-cell:filled:selected:hover"));
        assertTrue(css.indexOf(".combo-box-popup .list-cell:filled:selected {")
                < css.indexOf(".combo-box-popup .list-cell:filled:hover,"));
    }

    private static String stylesheet() throws IOException {
        try (InputStream input = SelectionStylesTest.class.getResourceAsStream(
                "/evidencelogger/ui/selection.css")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
