package io.github.escumartin.neocatecumenal.identity.application.port.in;

import java.time.Duration;

public sealed interface LoginResult {

    record LoggedIn(String token, MyProfile profile) implements LoginResult {
    }

    record InvalidCredentials() implements LoginResult {
    }

    record Blocked(Duration retryAfter) implements LoginResult {
    }
}
