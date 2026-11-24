package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.UserId;

public interface DeleteUser {

    void delete(UserId userId);
}
