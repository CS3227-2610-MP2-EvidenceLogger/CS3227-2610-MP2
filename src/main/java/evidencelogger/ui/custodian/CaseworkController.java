package evidencelogger.ui.custodian;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.EvidenceId;
import evidencelogger.domain.StorageLocationId;
import evidencelogger.service.ServiceException;
import evidencelogger.service.casework.CaseworkCommandService;
import evidencelogger.service.casework.CaseworkQueryService;
import evidencelogger.service.dto.CaseworkCommands;
import evidencelogger.service.dto.CaseworkViews;

/** Presentation logic for the Custodian casework screens. */
public final class CaseworkController {
    private final CaseworkCommandService commands;
    private final CaseworkQueryService queries;

    /** Creates a controller backed by the authorized casework services. */
    public CaseworkController(
            CaseworkCommandService commands, CaseworkQueryService queries) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.queries = Objects.requireNonNull(queries, "queries");
    }

    /** Creates a case with the selected initial Investigator. */
    public Result<CaseId> createCase(
            String title, CaseworkViews.Investigator investigator) {
        if (investigator == null) {
            return Result.failure("Select an initial Investigator");
        }
        return execute(() -> commands.createCase(new CaseworkCommands.CreateCase(
                title, investigator.investigatorId())));
    }

    /** Adds the selected Investigator to the selected case. */
    public Result<Void> addAssignment(
            CaseworkViews.Case selectedCase,
            CaseworkViews.Investigator investigator) {
        if (selectedCase == null) {
            return Result.failure("Select a case");
        }
        if (investigator == null) {
            return Result.failure("Select an Investigator");
        }
        return execute(() -> {
            commands.addAssignment(new CaseworkCommands.AddAssignment(
                    selectedCase.caseId(), investigator.investigatorId()));
            return null;
        });
    }

    /** Removes the selected assignment from the selected case. */
    public Result<Void> removeAssignment(
            CaseworkViews.Case selectedCase,
            CaseworkViews.Investigator investigator) {
        if (selectedCase == null) {
            return Result.failure("Select a case");
        }
        if (investigator == null) {
            return Result.failure("Select an assigned Investigator");
        }
        return execute(() -> {
            commands.removeAssignment(new CaseworkCommands.RemoveAssignment(
                    selectedCase.caseId(), investigator.investigatorId()));
            return null;
        });
    }

    /** Adds a storage location to the selectable list. */
    public Result<StorageLocationId> addStorageLocation(String name) {
        return execute(() -> commands.addStorageLocation(
                new CaseworkCommands.AddStorageLocation(name)));
    }

    /** Registers evidence against the selected case and storage location. */
    public Result<EvidenceId> registerEvidence(
            CaseworkViews.Case selectedCase,
            String description,
            CaseworkViews.StorageLocation location) {
        if (selectedCase == null) {
            return Result.failure("Select a case");
        }
        if (location == null) {
            return Result.failure("Select a storage location");
        }
        return execute(() -> commands.registerEvidence(
                new CaseworkCommands.RegisterEvidence(
                        selectedCase.caseId(),
                        description,
                        location.storageLocationId())));
    }

    /** Searches cases within the signed-in actor's authorized scope. */
    public Result<List<CaseworkViews.Case>> searchCases(String searchText) {
        return execute(() -> queries.searchCases(searchText));
    }

    /** Searches evidence within the signed-in actor's authorized scope. */
    public Result<List<CaseworkViews.Evidence>> searchEvidence(String searchText) {
        return execute(() -> queries.searchEvidence(searchText));
    }

    /** Lists Investigators available for assignment. */
    public Result<List<CaseworkViews.Investigator>> listInvestigators() {
        return execute(queries::listInvestigators);
    }

    /** Lists current assignments for one case. */
    public Result<List<CaseworkViews.Investigator>> listAssignments(
            CaseworkViews.Case selectedCase) {
        if (selectedCase == null) {
            return Result.failure("Select a case");
        }
        return execute(() -> queries.listAssignments(selectedCase.caseId()));
    }

    /** Lists storage locations available for evidence registration. */
    public Result<List<CaseworkViews.StorageLocation>> listStorageLocations() {
        return execute(queries::listStorageLocations);
    }

    private static <T> Result<T> execute(Supplier<T> operation) {
        try {
            return Result.success(operation.get());
        } catch (ServiceException exception) {
            return Result.failure(exception.getMessage());
        }
    }

    /** Outcome rendered by a casework screen without exposing service exceptions. */
    public record Result<T>(boolean successful, T value, String message) {
        /** Validates the presentation outcome. */
        public Result {
            Objects.requireNonNull(message, "message");
        }

        private static <T> Result<T> success(T value) {
            return new Result<>(true, value, "");
        }

        private static <T> Result<T> failure(String message) {
            return new Result<>(false, null, message);
        }
    }
}
