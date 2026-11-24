package io.github.escumartin.neocatecumenal.identity.application.port.in;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("USER_NOT_FOUND");
    }
}
