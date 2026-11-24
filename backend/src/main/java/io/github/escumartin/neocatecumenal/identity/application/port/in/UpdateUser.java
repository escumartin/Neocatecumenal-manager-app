package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.User;

public interface UpdateUser {

    User update(UpdateUserCommand command);
}
