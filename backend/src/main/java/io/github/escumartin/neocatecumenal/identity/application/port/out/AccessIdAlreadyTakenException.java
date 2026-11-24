package io.github.escumartin.neocatecumenal.identity.application.port.out;

public class AccessIdAlreadyTakenException extends RuntimeException {

    public AccessIdAlreadyTakenException() {
        super("ACCESS_ID_ALREADY_TAKEN");
    }
}
