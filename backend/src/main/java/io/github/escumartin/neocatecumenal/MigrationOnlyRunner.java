package io.github.escumartin.neocatecumenal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("migrate")
class MigrationOnlyRunner implements ApplicationRunner {

    private final ConfigurableApplicationContext context;

    MigrationOnlyRunner(ConfigurableApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        SpringApplication.exit(context, () -> 0);
    }
}
