package io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest;

import java.time.Duration;

class LoginBlockedException extends RuntimeException {

    private final long retryAfterSeconds;

    LoginBlockedException(Duration retryAfter) {
        super("Inicio de sesión bloqueado temporalmente");
        long seconds = (retryAfter.toMillis() + 999) / 1000;
        this.retryAfterSeconds = Math.max(1, seconds);
    }

    long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
