package evidencelogger.ui.custodian;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.EvidenceCustodyState;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.domain.UserId;
import evidencelogger.service.ServiceException;
import evidencelogger.service.casework.CaseworkCommandService;
import evidencelogger.service.casework.CaseworkQueryService;
import evidencelogger.service.dto.CaseworkCommands;
import evidencelogger.service.dto.CaseworkViews;

class CaseworkControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-25T03:00:00Z");
    private static final CaseId CASE_ID = CaseId.parse(
            "00000000-0000-0000-0000-000000000101");
    private static final UserId INVESTIGATOR_ID = UserId.parse(
            "00000000-0000-0000-0000-000000000102");
    private static final StorageLocationId LOCATION_ID = StorageLocationId.parse(
            "00000000-0000-0000-0000-000000000103");
    private static final EvidenceId EVIDENCE_ID = EvidenceId.parse(
            "00000000-0000-0000-0000-000000000104");

    private FakeCaseworkService service;
    private CaseworkController controller;
    private CaseworkViews.Case selectedCase;
    private CaseworkViews.Investigator investigator;
    private CaseworkViews.StorageLocation location;

    @BeforeEach
    void setUp() {
        service = new FakeCaseworkService();
        controller = new CaseworkController(service, service);
        selectedCase = new CaseworkViews.Case(CASE_ID, "Harbour case", NOW);
        investigator = new CaseworkViews.Investigator(
                INVESTIGATOR_ID, "alex.investigator", "Alex Investigator");
        location = new CaseworkViews.StorageLocation(LOCATION_ID, "Locker A", NOW);
        service.cases = List.of(selectedCase);
        service.investigators = List.of(investigator);
        service.locations = List.of(location);
        service.evidence = List.of(new CaseworkViews.Evidence(
                EVIDENCE_ID,
                CASE_ID,
                selectedCase.title(),
                "EV-104",
                "Blue notebook",
                LOCATION_ID,
                location.name(),
                EvidenceCustodyState.IN_STORAGE,
                NOW));
    }

    @Test
    void commandsUseSelectedRecordsAndEnteredText() {
        assertEquals(CASE_ID,
                controller.createCase("  Harbour case  ", investigator).value());
        assertEquals(
                new CaseworkCommands.CreateCase("  Harbour case  ", INVESTIGATOR_ID),
                service.createCaseCommand);

        assertTrue(controller.addAssignment(selectedCase, investigator).successful());
        assertEquals(
                new CaseworkCommands.AddAssignment(CASE_ID, INVESTIGATOR_ID),
                service.addAssignmentCommand);

        assertTrue(controller.removeAssignment(selectedCase, investigator).successful());
        assertEquals(
                new CaseworkCommands.RemoveAssignment(CASE_ID, INVESTIGATOR_ID),
                service.removeAssignmentCommand);

        assertEquals(LOCATION_ID, controller.addStorageLocation(" Locker A ").value());
        assertEquals(
                new CaseworkCommands.AddStorageLocation(" Locker A "),
                service.addLocationCommand);

        assertEquals(EVIDENCE_ID,
                controller.registerEvidence(selectedCase, " Blue notebook ", location).value());
        assertEquals(
                new CaseworkCommands.RegisterEvidence(
                        CASE_ID, " Blue notebook ", LOCATION_ID),
                service.registerEvidenceCommand);
    }

    @Test
    void queriesReturnAuthorizedServiceResultsAndForwardSearchText() {
        assertEquals(service.cases, controller.searchCases(" harbour ").value());
        assertEquals(" harbour ", service.caseSearchText);
        assertEquals(service.evidence, controller.searchEvidence(" EV-104 ").value());
        assertEquals(" EV-104 ", service.evidenceSearchText);
        assertEquals(service.investigators, controller.listInvestigators().value());
        assertEquals(service.investigators,
                controller.listAssignments(selectedCase).value());
        assertEquals(CASE_ID, service.assignmentCaseId);
        assertEquals(service.locations, controller.listStorageLocations().value());
    }

    @Test
    void missingSelectionsAreRejectedWithoutCallingServices() {
        CaseworkController.Result<CaseId> create = controller.createCase("Case", null);
        CaseworkController.Result<Void> add = controller.addAssignment(null, investigator);
        CaseworkController.Result<Void> remove = controller.removeAssignment(selectedCase, null);
        CaseworkController.Result<EvidenceId> registration =
                controller.registerEvidence(selectedCase, "Item", null);
        CaseworkController.Result<List<CaseworkViews.Investigator>> assignments =
                controller.listAssignments(null);

        assertFalse(create.successful());
        assertEquals("Select an initial Investigator", create.message());
        assertEquals("Select a case", add.message());
        assertEquals("Select an assigned Investigator", remove.message());
        assertEquals("Select a storage location", registration.message());
        assertEquals("Select a case", assignments.message());
        assertEquals(0, service.commandCalls);
        assertNull(service.assignmentCaseId);
    }

    @Test
    void typedServiceFailureBecomesReadablePresentationResult() {
        service.failure = new ServiceException.Conflict("Location already exists");

        CaseworkController.Result<StorageLocationId> result =
                controller.addStorageLocation("Locker A");

        assertFalse(result.successful());
        assertNull(result.value());
        assertEquals("Location already exists", result.message());
    }

    @Test
    void storageFailureIncludesDiagnosticReference() {
        service.failure = new ServiceException.StorageFailure(
                "Casework data could not be read",
                new IllegalStateException("database unavailable"));

        CaseworkController.Result<StorageLocationId> result =
                controller.addStorageLocation("Locker A");

        assertFalse(result.successful());
        assertTrue(result.message().startsWith(
                "Casework data could not be read. Reference: "));
    }

    private static final class FakeCaseworkService
            implements CaseworkCommandService, CaseworkQueryService {
        private List<CaseworkViews.Case> cases = List.of();
        private List<CaseworkViews.Evidence> evidence = List.of();
        private List<CaseworkViews.Investigator> investigators = List.of();
        private List<CaseworkViews.StorageLocation> locations = List.of();
        private RuntimeException failure;
        private CaseworkCommands.CreateCase createCaseCommand;
        private CaseworkCommands.AddAssignment addAssignmentCommand;
        private CaseworkCommands.RemoveAssignment removeAssignmentCommand;
        private CaseworkCommands.AddStorageLocation addLocationCommand;
        private CaseworkCommands.RegisterEvidence registerEvidenceCommand;
        private String caseSearchText;
        private String evidenceSearchText;
        private CaseId assignmentCaseId;
        private int commandCalls;

        @Override
        public CaseId createCase(CaseworkCommands.CreateCase command) {
            beforeCommand();
            createCaseCommand = command;
            return CASE_ID;
        }

        @Override
        public void addAssignment(CaseworkCommands.AddAssignment command) {
            beforeCommand();
            addAssignmentCommand = command;
        }

        @Override
        public void removeAssignment(CaseworkCommands.RemoveAssignment command) {
            beforeCommand();
            removeAssignmentCommand = command;
        }

        @Override
        public StorageLocationId addStorageLocation(
                CaseworkCommands.AddStorageLocation command) {
            beforeCommand();
            addLocationCommand = command;
            return LOCATION_ID;
        }

        @Override
        public EvidenceId registerEvidence(CaseworkCommands.RegisterEvidence command) {
            beforeCommand();
            registerEvidenceCommand = command;
            return EVIDENCE_ID;
        }

        @Override
        public List<CaseworkViews.Case> searchCases(String searchText) {
            caseSearchText = searchText;
            return cases;
        }

        @Override
        public List<CaseworkViews.Evidence> searchEvidence(String searchText) {
            evidenceSearchText = searchText;
            return evidence;
        }

        @Override
        public List<CaseworkViews.Investigator> listInvestigators() {
            return investigators;
        }

        @Override
        public List<CaseworkViews.Investigator> listAssignments(CaseId caseId) {
            assignmentCaseId = caseId;
            return investigators;
        }

        @Override
        public List<CaseworkViews.StorageLocation> listStorageLocations() {
            return locations;
        }

        private void beforeCommand() {
            commandCalls++;
            if (failure != null) {
                throw failure;
            }
        }
    }
}
