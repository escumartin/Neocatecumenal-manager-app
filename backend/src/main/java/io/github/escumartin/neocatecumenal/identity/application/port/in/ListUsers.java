package io.github.escumartin.neocatecumenal.identity.application.port.in;

public interface ListUsers {

    UserPage list(ListUsersQuery query);
}
