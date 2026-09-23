package evidencelogger.infrastructure.db;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;

import org.junit.jupiter.api.Test;

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
}
