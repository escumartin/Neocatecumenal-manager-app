package io.github.escumartin.neocatecumenal.identity.application.port.in;

public interface CreateUser {

    CreatedUserResult create(CreateUserCommand command);
}
