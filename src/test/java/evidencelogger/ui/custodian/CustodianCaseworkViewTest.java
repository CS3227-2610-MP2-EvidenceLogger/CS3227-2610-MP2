package evidencelogger.ui.custodian;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import evidencelogger.ui.common.WorkspaceHeader;

class CustodianCaseworkViewTest {
    @Test
    void configuresCustodianHeaderWithAuthenticatedNameAndSignOutAction() {
        AtomicBoolean signedOut = new AtomicBoolean();

        WorkspaceHeader.Configuration header = CustodianCaseworkView.headerConfiguration(
                "Casey Custodian", () -> signedOut.set(true));

        assertEquals("EvidenceLogger   |   Custodian Workspace", header.title());
        assertEquals("Casey Custodian", header.displayName());
        assertFalse(signedOut.get());
        header.signOut().run();
        assertTrue(signedOut.get());
    }
}
