package evidencelogger.ui.custodian;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.service.dto.CaseworkViews;
import evidencelogger.ui.common.WorkspaceHeader;

class CustodianCaseworkViewTest {
    private static final Instant NOW = Instant.parse("2026-09-26T06:00:00Z");

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

    @Test
    void evidenceTableShowsOnlyTheFourRequestedSummaryColumns() {
        assertEquals(List.of("Description", "Location", "Case", "Status"),
                CustodianCaseworkView.evidenceColumns());
    }

    @Test
    void duplicateWarningRequiresSameCaseDescriptionAndLocation() {
        CaseworkViews.Case selectedCase = caseView("Case One");
        CaseworkViews.StorageLocation selectedLocation = location("Locker A");
        CaseworkViews.Evidence existing = evidence(
                selectedCase, selectedLocation, "Blue notebook",
                EvidenceCustodyState.IN_STORAGE);

        assertTrue(CustodianCaseworkView.possibleDuplicate(
                List.of(existing), selectedCase, " blue notebook ", selectedLocation));
        assertFalse(CustodianCaseworkView.possibleDuplicate(
                List.of(existing), selectedCase, "Red notebook", selectedLocation));
        assertFalse(CustodianCaseworkView.possibleDuplicate(
                List.of(existing), selectedCase, "Blue notebook", location("Locker B")));
    }

    @Test
    void evidenceFiltersAndVoidOfferUseVisibleBusinessFields() {
        CaseworkViews.Case firstCase = caseView("Case One");
        CaseworkViews.Case secondCase = caseView("Case Two");
        CaseworkViews.StorageLocation locker = location("Locker A");
        CaseworkViews.Evidence stored = evidence(
                firstCase, locker, "Stored item", EvidenceCustodyState.IN_STORAGE);
        CaseworkViews.Evidence checkedOut = evidence(
                secondCase, locker, "Checked item", EvidenceCustodyState.CHECKED_OUT);

        assertEquals(List.of(stored), CustodianCaseworkView.filterEvidence(
                List.of(stored, checkedOut), firstCase, null, null));
        assertEquals(List.of(checkedOut), CustodianCaseworkView.filterEvidence(
                List.of(stored, checkedOut), null, locker,
                EvidenceCustodyState.CHECKED_OUT));
        assertTrue(CustodianCaseworkView.canOfferVoid(stored));
        assertFalse(CustodianCaseworkView.canOfferVoid(checkedOut));
    }

    private static CaseworkViews.Case caseView(String title) {
        return new CaseworkViews.Case(new CaseId(UUID.randomUUID()), title, NOW);
    }

    private static CaseworkViews.StorageLocation location(String name) {
        return new CaseworkViews.StorageLocation(
                new StorageLocationId(UUID.randomUUID()), name, NOW);
    }

    private static CaseworkViews.Evidence evidence(
            CaseworkViews.Case selectedCase,
            CaseworkViews.StorageLocation selectedLocation,
            String description,
            EvidenceCustodyState state) {
        EvidenceId evidenceId = new EvidenceId(UUID.randomUUID());
        return new CaseworkViews.Evidence(
                evidenceId,
                selectedCase.caseId(),
                selectedCase.title(),
                "EV-" + evidenceId,
                description,
                selectedLocation.storageLocationId(),
                selectedLocation.name(),
                state,
                NOW);
    }
}
