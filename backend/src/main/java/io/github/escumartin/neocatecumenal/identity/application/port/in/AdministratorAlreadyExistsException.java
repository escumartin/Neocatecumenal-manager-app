package io.github.escumartin.neocatecumenal.identity.application.port.in;

public class AdministratorAlreadyExistsException extends RuntimeException {

    public AdministratorAlreadyExistsException() {
        super("Ya existe un administrador");
    }
}
