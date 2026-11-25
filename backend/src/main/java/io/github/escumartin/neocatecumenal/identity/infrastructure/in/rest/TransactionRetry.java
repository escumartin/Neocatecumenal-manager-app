package io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class TransactionRetry {

    private static final Logger log = LoggerFactory.getLogger(TransactionRetry.class);
    private static final int MAX_CALLS = 3;

    <T> T execute(Supplier<T> action) {
        StaleLockOrderException last = null;
        for (int call = 1; call <= MAX_CALLS; call++) {
            try {
                return action.get();
            } catch (StaleLockOrderException stale) {
                last = stale;
                log.atWarn().addKeyValue("call", call).log("Orden de bloqueos obsoleto, se reintenta la transacción");
            }
        }
        log.atWarn().addKeyValue("calls", MAX_CALLS).log("Reintentos de transacción agotados");
        throw last;
    }

    void run(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }
}
