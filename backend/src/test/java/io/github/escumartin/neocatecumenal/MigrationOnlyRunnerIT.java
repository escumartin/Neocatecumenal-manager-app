package io.github.escumartin.neocatecumenal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MigrationOnlyRunnerIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static Map<String, Object> migrateProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("DATABASE_URL", POSTGRES.getJdbcUrl());
        properties.put("spring.datasource.username", POSTGRES.getUsername());
        properties.put("spring.datasource.password", POSTGRES.getPassword());
        properties.put("ALLOWED_ORIGINS", "https://migrate.example.test");
        properties.put("PRIVACY_NOTICE_VERSION", "v1");
        properties.put("MINIMUM_APP_VERSION", "1.0.0");
        return properties;
    }

    private static JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()));
    }

    private static Integer historyCount() {
        return jdbc().queryForObject("select count(*) from flyway_schema_history", Integer.class);
    }

    private static ConfigurableApplicationContext runMigrate(Map<String, Object> properties) {
        return new SpringApplicationBuilder(NeocatecumenalApplication.class)
                .profiles("prod", "migrate")
                .properties(properties)
                .run();
    }

    @Test
    void appliesTheSchemaExitsWithZeroAndOpensNoPort() {
        ConfigurableApplicationContext context = runMigrate(migrateProperties());

        assertThat(context.isActive()).isFalse();
        assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
        assertThat(historyCount()).isPositive();
    }

    @Test
    void failsWithABrokenMigrationLeavingTheSchemaHistoryUntouched(@TempDir Path brokenMigrations)
            throws IOException {
        runMigrate(migrateProperties());
        Integer before = historyCount();
        Files.writeString(brokenMigrations.resolve("V9999__broken.sql"),
                "create table broken_probe(id int);\nselect from from;\n");
        Map<String, Object> properties = migrateProperties();
        properties.put("spring.flyway.locations", "classpath:db/migration,filesystem:" + brokenMigrations);

        assertThatThrownBy(() -> runMigrate(properties))
                .hasStackTraceContaining("V9999__broken.sql")
                .rootCause()
                .hasMessageContaining("syntax error");

        assertThat(historyCount()).isEqualTo(before);
        assertThat(jdbc().queryForObject("select to_regclass('broken_probe') is null", Boolean.class)).isTrue();
    }
}
