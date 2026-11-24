package io.github.escumartin.neocatecumenal.identity.application.port.out;

import io.github.escumartin.neocatecumenal.identity.domain.UserId;

public interface PasswordChangeAttempts {

    int recordFailure(UserId userId);

    void clear(UserId userId);
}
