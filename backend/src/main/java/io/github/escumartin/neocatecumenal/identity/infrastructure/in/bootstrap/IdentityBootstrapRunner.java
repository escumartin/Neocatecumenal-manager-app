package io.github.escumartin.neocatecumenal.identity.infrastructure.in.bootstrap;

import io.github.escumartin.neocatecumenal.identity.application.port.in.AdministratorAlreadyExistsException;
import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapIdentity;
import io.github.escumartin.neocatecumenal.identity.application.port.in.CreatedAdministrator;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.infrastructure.config.IdentityProperties;
import java.io.PrintWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public class IdentityBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IdentityBootstrapRunner.class);

    private final BootstrapIdentity bootstrap;
    private final BootstrapConsoleProvider console;
    private final BootstrapTermination termination;
    private final IdentityProperties.Bootstrap properties;

    public IdentityBootstrapRunner(BootstrapIdentity bootstrap, BootstrapConsoleProvider console,
            BootstrapTermination termination, IdentityProperties properties) {
        this.bootstrap = bootstrap;
        this.console = console;
        this.termination = termination;
        this.properties = properties.bootstrap();
    }

    @Override
    public void run(ApplicationArguments args) {
        PrintWriter writer = console.writer().orElseThrow(
                () -> abort("Carga inicial cancelada: hace falta una consola interactiva"));
        BootstrapCommand command = command();
        CreatedAdministrator administrator = create(command);
        log.atInfo().log("Carga inicial completada");
        writer.println("Identificador de acceso: " + administrator.accessId().value());
        writer.println("Contraseña inicial: " + administrator.initialPassword().value());
        writer.flush();
        termination.finish();
    }

    private CreatedAdministrator create(BootstrapCommand command) {
        try {
            return bootstrap.run(command);
        } catch (AdministratorAlreadyExistsException exists) {
            throw abort("Carga inicial cancelada: ya existe un administrador");
        }
    }

    private BootstrapCommand command() {
        if (properties.administratorName() == null || properties.administratorName().isBlank()
                || properties.administratorCommunity() == null) {
            throw abort("Carga inicial cancelada: falta el nombre o la comunidad del administrador");
        }
        return new BootstrapCommand(new DisplayName(properties.administratorName()),
                new CommunityNumber(properties.administratorCommunity()));
    }

    private static BootstrapAbortedException abort(String message) {
        log.atError().log(message);
        return new BootstrapAbortedException(message);
    }
}
