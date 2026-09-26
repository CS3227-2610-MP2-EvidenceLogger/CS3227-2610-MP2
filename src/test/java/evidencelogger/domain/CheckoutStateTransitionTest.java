package evidencelogger.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CheckoutStateTransitionTest {
    @Test
    void pendingRequestHasThreeDecisionPaths() {
        assertTrue(CheckoutRequestStatus.PENDING
                .canTransitionTo(CheckoutRequestStatus.APPROVED));
        assertTrue(CheckoutRequestStatus.PENDING
                .canTransitionTo(CheckoutRequestStatus.REJECTED));
        assertTrue(CheckoutRequestStatus.PENDING
                .canTransitionTo(CheckoutRequestStatus.WITHDRAWN));
    }

    @Test
    void approvedRequestCanBeConsumedOrCancelledOnly() {
        assertTrue(CheckoutRequestStatus.APPROVED
                .canTransitionTo(CheckoutRequestStatus.CONSUMED));
        assertTrue(CheckoutRequestStatus.APPROVED
                .canTransitionTo(CheckoutRequestStatus.CANCELLED));
        assertFalse(CheckoutRequestStatus.APPROVED
                .canTransitionTo(CheckoutRequestStatus.REJECTED));
        assertFalse(CheckoutRequestStatus.APPROVED
                .canTransitionTo(CheckoutRequestStatus.PENDING));
    }

    @Test
    void terminalRequestStatesCannotAdvanceOrRepeat() {
        for (CheckoutRequestStatus status : CheckoutRequestStatus.values()) {
            if (status.isTerminal()) {
                for (CheckoutRequestStatus target : CheckoutRequestStatus.values()) {
                    assertFalse(status.canTransitionTo(target));
                }
            }
        }
    }

    @Test
    void custodyStatesModelHandoffWithoutGrantingCollection() {
        assertTrue(EvidenceCustodyState.IN_STORAGE
                .canTransitionTo(EvidenceCustodyState.HANDOFF_AWAITING_ACK));
        assertTrue(EvidenceCustodyState.HANDOFF_AWAITING_ACK
                .canTransitionTo(EvidenceCustodyState.IN_STORAGE));
        assertTrue(EvidenceCustodyState.HANDOFF_AWAITING_ACK
                .canTransitionTo(EvidenceCustodyState.CHECKED_OUT));
        assertFalse(EvidenceCustodyState.IN_STORAGE
                .canTransitionTo(EvidenceCustodyState.CHECKED_OUT));
    }

    @Test
    void checkoutRequiresCustodianReceiptBeforeStorage() {
        assertTrue(EvidenceCustodyState.CHECKED_OUT
                .canTransitionTo(EvidenceCustodyState.HANDIN_AWAITING_ACK));
        assertFalse(EvidenceCustodyState.CHECKED_OUT
                .canTransitionTo(EvidenceCustodyState.IN_STORAGE));
        assertTrue(EvidenceCustodyState.HANDIN_AWAITING_ACK
                .canTransitionTo(EvidenceCustodyState.IN_STORAGE));
        assertFalse(EvidenceCustodyState.HANDIN_AWAITING_ACK
                .canTransitionTo(EvidenceCustodyState.CHECKED_OUT));
    }

    @Test
    void onlyStoredEvidenceCanAdvanceToTerminalVoidedState() {
        assertTrue(EvidenceCustodyState.IN_STORAGE
                .canTransitionTo(EvidenceCustodyState.VOIDED));
        for (EvidenceCustodyState target : EvidenceCustodyState.values()) {
            assertFalse(EvidenceCustodyState.VOIDED.canTransitionTo(target));
        }
        assertFalse(EvidenceCustodyState.CHECKED_OUT
                .canTransitionTo(EvidenceCustodyState.VOIDED));
    }
}
