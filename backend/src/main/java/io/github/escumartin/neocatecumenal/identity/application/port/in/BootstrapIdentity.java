package io.github.escumartin.neocatecumenal.identity.application.port.in;

public interface BootstrapIdentity {

    BootstrapOutcome run(BootstrapCommand command);
}
