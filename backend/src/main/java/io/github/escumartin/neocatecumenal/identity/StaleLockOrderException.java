package io.github.escumartin.neocatecumenal.identity;

public class StaleLockOrderException extends RuntimeException {

    public StaleLockOrderException() {
        super("STALE_LOCK_ORDER");
    }
}
