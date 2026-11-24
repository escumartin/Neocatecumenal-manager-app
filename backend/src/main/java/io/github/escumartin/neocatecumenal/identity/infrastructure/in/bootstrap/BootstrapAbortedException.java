package io.github.escumartin.neocatecumenal.identity.infrastructure.in.bootstrap;

import org.springframework.boot.ExitCodeGenerator;

public class BootstrapAbortedException extends IllegalStateException implements ExitCodeGenerator {

    private static final int EXIT_CODE = 2;

    public BootstrapAbortedException(String message) {
        super(message);
    }

    @Override
    public int getExitCode() {
        return EXIT_CODE;
    }
}
