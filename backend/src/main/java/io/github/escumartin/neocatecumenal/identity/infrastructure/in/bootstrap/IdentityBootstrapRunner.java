package io.github.escumartin.neocatecumenal.identity.infrastructure.in.bootstrap;

import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapIdentity;
import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapOutcome;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.infrastructure.config.IdentityProperties;
import java.io.PrintWriter;
import java.util.List;
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
        BootstrapOutcome outcome = bootstrap.run(command);
        log.atInfo()
                .addKeyValue("createdCommunities", outcome.createdCommunities())
                .addKeyValue("administratorCreated", outcome.administrator().isPresent())
                .log("Carga inicial completada");
        outcome.administrator().ifPresent(administrator -> {
            writer.println("Identificador de acceso: " + administrator.accessId().value());
            writer.println("Contraseña inicial: " + administrator.initialPassword().value());
            writer.flush();
        });
        termination.finish();
    }

    private BootstrapCommand command() {
        if (properties.administratorName() == null || properties.administratorName().isBlank()
                || properties.administratorCommunity() == null) {
            throw abort("Carga inicial cancelada: falta el nombre o la comunidad del administrador");
        }
        List<CommunityNumber> numbers = properties.communities().stream().map(CommunityNumber::new).toList();
        return new BootstrapCommand(numbers, new DisplayName(properties.administratorName()),
                new CommunityNumber(properties.administratorCommunity()));
    }

    private static BootstrapAbortedException abort(String message) {
        log.atError().log(message);
        return new BootstrapAbortedException(message);
    }
}
