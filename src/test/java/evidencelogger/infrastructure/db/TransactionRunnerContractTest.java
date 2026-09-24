package evidencelogger.infrastructure.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import evidencelogger.service.ServiceException;

class TransactionRunnerContractTest {
    @Test
    void workReceivesTheRunnerOwnedConnection() {
        InvocationHandler noOpHandler = (proxy, method, arguments) -> null;
        Connection runnerOwnedConnection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                noOpHandler);

        TransactionRunner runner = new TransactionRunner() {
            @Override
            public <T> T inTransaction(TransactionalWork<T> work) {
                return work.execute(runnerOwnedConnection);
            }
        };

        Connection receivedConnection = runner.inTransaction(connection -> connection);

        assertSame(runnerOwnedConnection, receivedConnection);
    }

    @Test
    void commitFailureRollsBackAndPreservesTheCommitException() {
        AtomicBoolean autoCommit = new AtomicBoolean(true);
        AtomicBoolean rollbackCalled = new AtomicBoolean();
        SQLException commitFailure = new SQLException("injected commit failure");
        InvocationHandler handler = (proxy, method, arguments) -> switch (method.getName()) {
        case "getAutoCommit" -> autoCommit.get();
        case "setAutoCommit" -> {
            autoCommit.set((boolean) arguments[0]);
            yield null;
        }
        case "commit" -> throw commitFailure;
        case "rollback" -> {
            rollbackCalled.set(true);
            yield null;
        }
        case "close" -> null;
        case "isClosed" -> false;
        default -> throw new UnsupportedOperationException(method.getName());
        };
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                handler);
        JdbcTransactionRunner runner = new JdbcTransactionRunner(() -> connection);

        var failure = assertThrows(ServiceException.StorageFailure.class, () ->
                runner.inTransaction(ignored -> "result"));

        assertSame(commitFailure, failure.getCause());
        assertTrue(rollbackCalled.get());
        assertTrue(autoCommit.get());
        assertInstanceOf(SQLException.class, failure.getCause());
    }

    @Test
    void postCommitCleanupFailuresDoNotChangeTheSuccessfulOutcome() {
        AtomicBoolean autoCommit = new AtomicBoolean(true);
        AtomicBoolean committed = new AtomicBoolean();
        AtomicBoolean closeAttempted = new AtomicBoolean();
        InvocationHandler handler = (proxy, method, arguments) -> switch (method.getName()) {
        case "getAutoCommit" -> autoCommit.get();
        case "setAutoCommit" -> {
            boolean requestedValue = (boolean) arguments[0];
            if (requestedValue && committed.get()) {
                throw new SQLException("injected restore failure");
            }
            autoCommit.set(requestedValue);
            yield null;
        }
        case "commit" -> {
            committed.set(true);
            yield null;
        }
        case "rollback" -> null;
        case "close" -> {
            closeAttempted.set(true);
            throw new SQLException("injected close failure");
        }
        case "isClosed" -> false;
        default -> throw new UnsupportedOperationException(method.getName());
        };
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                handler);
        JdbcTransactionRunner runner = new JdbcTransactionRunner(() -> connection);

        String result = runner.inTransaction(ignored -> "committed result");

        assertEquals("committed result", result);
        assertTrue(committed.get());
        assertTrue(closeAttempted.get());
    }
}
