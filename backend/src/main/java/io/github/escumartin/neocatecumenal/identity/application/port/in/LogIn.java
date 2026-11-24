package io.github.escumartin.neocatecumenal.identity.application.port.in;

public interface LogIn {

    LoginResult logIn(LoginCommand command);
}
