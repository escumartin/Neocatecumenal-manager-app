package io.github.escumartin.neocatecumenal.identity.infrastructure.config;

import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapIdentity;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.bootstrap.BootstrapConsoleProvider;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.bootstrap.BootstrapTermination;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.bootstrap.IdentityBootstrapRunner;
import java.io.Console;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "identity.bootstrap", name = "enabled", havingValue = "true")
class IdentityBootstrapConfig {

    @Bean
    @ConditionalOnMissingBean
    BootstrapConsoleProvider systemConsoleProvider() {
        return () -> {
            Console console = System.console();
            return console == null ? Optional.empty() : Optional.of(console.writer());
        };
    }

    @Bean
    @ConditionalOnMissingBean
    BootstrapTermination springApplicationTermination(ConfigurableApplicationContext context) {
        return () -> System.exit(SpringApplication.exit(context));
    }

    @Bean
    IdentityBootstrapRunner identityBootstrapRunner(BootstrapIdentity bootstrap, BootstrapConsoleProvider console,
            BootstrapTermination termination, IdentityProperties properties) {
        return new IdentityBootstrapRunner(bootstrap, console, termination, properties);
    }
}
