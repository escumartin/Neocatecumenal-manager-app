package io.github.escumartin.neocatecumenal.community.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("communityTransactionRetry")
public class TransactionRetry {

    private static final Logger log = LoggerFactory.getLogger(TransactionRetry.class);
    private static final int MAX_CALLS = 3;

    public <T> T execute(Supplier<T> action) {
        StaleLockOrderException last = null;
        for (int call = 1; call <= MAX_CALLS; call++) {
            try {
                return action.get();
            } catch (StaleLockOrderException stale) {
                last = stale;
                log.atWarn().addKeyValue("call", call).log("Orden de bloqueos obsoleto, se reintenta la transacción");
            }
        }
        throw last;
    }

    public void run(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }
}
