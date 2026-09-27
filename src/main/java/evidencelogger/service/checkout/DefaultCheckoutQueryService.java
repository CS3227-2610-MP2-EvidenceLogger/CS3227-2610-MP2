package evidencelogger.service.checkout;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import evidencelogger.domain.CaseId;
import evidencelogger.domain.CheckoutId;
import evidencelogger.domain.CheckoutRequestId;
import evidencelogger.domain.CheckoutRequestStatus;
import evidencelogger.domain.Role;
import evidencelogger.domain.UserId;
import evidencelogger.infrastructure.db.TransactionRunner;
import evidencelogger.repository.RepositoryException;
import evidencelogger.repository.checkout.CheckoutReadRepository;
import evidencelogger.service.ServiceException;
import evidencelogger.service.auth.AuthenticatedSession;
import evidencelogger.service.auth.AuthorizationService;
import evidencelogger.service.auth.SessionProvider;
import evidencelogger.service.dto.CheckoutViews;

/** Authorized checkout reads for Custodian and assigned Investigator views. */
public final class DefaultCheckoutQueryService implements CheckoutQueryService {
    private final TransactionRunner transactions;
    private final AuthorizationService authorization;
    private final SessionProvider sessions;
    private final CheckoutReadRepository reads;

    /** Creates checkout queries with their authorization, session, and persistence collaborators. */
    public DefaultCheckoutQueryService(
            TransactionRunner transactions,
            AuthorizationService authorization,
            SessionProvider sessions,
            CheckoutReadRepository reads) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.reads = Objects.requireNonNull(reads, "reads");
    }

    @Override
    public List<CheckoutViews.Request> listRequests() {
        return listRequestsWithStatus(Optional.empty());
    }

    @Override
    public List<CheckoutViews.Request> listRequests(CheckoutRequestStatus status) {
        return listRequestsWithStatus(Optional.of(Objects.requireNonNull(status, "status")));
    }

    @Override
    public List<CheckoutViews.Request> listRequestsForCase(CaseId caseId) {
        Objects.requireNonNull(caseId, "caseId");
        Optional<UserId> scope = queryScopeForCase(caseId);
        return runRead(() -> transactions.inTransaction(connection ->
                reads.listRequestsForCase(connection, caseId, scope))).stream()
                .map(DefaultCheckoutQueryService::toView)
                .toList();
    }

    @Override
    public CheckoutViews.Request getRequest(CheckoutRequestId requestId) {
        Objects.requireNonNull(requestId, "requestId");
        Optional<UserId> scope = queryScope();
        return runRead(() -> transactions.inTransaction(connection ->
                reads.findRequest(connection, requestId, scope)))
                .map(DefaultCheckoutQueryService::toView)
                .orElseThrow(() -> new ServiceException.NotFound("Checkout request was not found"));
    }

    @Override
    public List<CheckoutViews.Checkout> listCheckouts() {
        Optional<UserId> scope = queryScope();
        return runRead(() -> transactions.inTransaction(connection ->
                reads.listCheckouts(connection, scope))).stream()
                .map(DefaultCheckoutQueryService::toView)
                .toList();
    }

    @Override
    public List<CheckoutViews.Checkout> listCheckoutsForCase(CaseId caseId) {
        Objects.requireNonNull(caseId, "caseId");
        Optional<UserId> scope = queryScopeForCase(caseId);
        return runRead(() -> transactions.inTransaction(connection ->
                reads.listCheckoutsForCase(connection, caseId, scope))).stream()
                .map(DefaultCheckoutQueryService::toView)
                .toList();
    }

    @Override
    public CheckoutViews.Checkout getCheckout(CheckoutId checkoutId) {
        Objects.requireNonNull(checkoutId, "checkoutId");
        Optional<UserId> scope = queryScope();
        return runRead(() -> transactions.inTransaction(connection ->
                reads.findCheckout(connection, checkoutId, scope)))
                .map(DefaultCheckoutQueryService::toView)
                .orElseThrow(() -> new ServiceException.NotFound("Checkout was not found"));
    }

    @Override
    public List<CheckoutViews.ExaminationNote> listNotes(CheckoutId checkoutId) {
        Objects.requireNonNull(checkoutId, "checkoutId");
        Optional<UserId> scope = queryScope();
        return runRead(() -> transactions.inTransaction(connection ->
                reads.listNotes(connection, checkoutId, scope))).stream()
                .map(DefaultCheckoutQueryService::toView)
                .toList();
    }

    private List<CheckoutViews.Request> listRequestsWithStatus(Optional<CheckoutRequestStatus> status) {
        Optional<UserId> scope = queryScope();
        return runRead(() -> transactions.inTransaction(connection ->
                reads.listRequests(connection, status, scope))).stream()
                .map(DefaultCheckoutQueryService::toView)
                .toList();
    }

    private Optional<UserId> queryScopeForCase(CaseId caseId) {
        AuthenticatedSession session = sessions.requireSession();
        if (session.role() == Role.INVESTIGATOR) {
            authorization.requireAssignedInvestigator(caseId);
        }
        return queryScope();
    }

    private Optional<UserId> queryScope() {
        AuthenticatedSession session = sessions.requireSession();
        if (session.role() == Role.EVIDENCE_CUSTODIAN) {
            authorization.requireCustodian();
            return Optional.empty();
        }
        if (session.role() == Role.INVESTIGATOR) {
            authorization.requireInvestigator();
            return Optional.of(session.userId());
        }
        throw new ServiceException.Forbidden("The signed-in role cannot read checkout data");
    }

    private static <T> T runRead(Supplier<T> query) {
        try {
            return query.get();
        } catch (RepositoryException exception) {
            throw CheckoutRepositoryErrors.translate(exception);
        }
    }

    private static CheckoutViews.Request toView(CheckoutReadRepository.RequestDetails details) {
        return new CheckoutViews.Request(
                details.requestId(),
                details.evidenceId(),
                details.evidenceReference(),
                details.evidenceDescription(),
                details.storageLocationName(),
                details.caseId(),
                details.caseTitle(),
                details.requesterId(),
                details.requesterDisplayName(),
                details.purpose(),
                details.expectedReturnAt(),
                details.status(),
                details.evidenceState(),
                details.submittedAt(),
                details.handoffId(),
                details.checkoutId());
    }

    private static CheckoutViews.Checkout toView(CheckoutReadRepository.CheckoutDetails details) {
        return new CheckoutViews.Checkout(
                details.checkoutId(),
                details.requestId(),
                details.evidenceId(),
                details.evidenceReference(),
                details.caseId(),
                details.caseTitle(),
                details.collectorId(),
                details.collectorDisplayName(),
                details.collectedAt(),
                details.returnInitiatedAt(),
                details.completedAt(),
                details.evidenceState());
    }

    private static CheckoutViews.ExaminationNote toView(
            CheckoutReadRepository.ExaminationNoteDetails details) {
        return new CheckoutViews.ExaminationNote(
                details.noteId(),
                details.checkoutId(),
                details.authorId(),
                details.authorDisplayName(),
                details.text(),
                details.createdAt(),
                details.corrections().stream()
                        .map(DefaultCheckoutQueryService::toView)
                        .toList());
    }

    private static CheckoutViews.NoteCorrection toView(
            CheckoutReadRepository.NoteCorrectionDetails details) {
        return new CheckoutViews.NoteCorrection(
                details.authorId(),
                details.authorDisplayName(),
                details.correctionText(),
                details.reason(),
                details.createdAt());
    }
}
