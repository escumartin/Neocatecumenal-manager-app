package io.github.escumartin.neocatecumenal.identity.infrastructure.in.bootstrap;

import java.io.PrintWriter;
import java.util.Optional;

public interface BootstrapConsoleProvider {

    Optional<PrintWriter> writer();
}
