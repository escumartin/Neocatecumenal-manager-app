package io.github.escumartin.neocatecumenal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class ProductionProfileIT {

    private static final String SECRET_SENTINEL = "sentinel-secret-value";
    private static final String ORIGIN_SENTINEL = "https://origin-sentinel.example.test";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static Map<String, Object> productionProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server.port", "0");
        properties.put("DATABASE_URL", POSTGRES.getJdbcUrl());
        properties.put("spring.datasource.username", POSTGRES.getUsername());
        properties.put("spring.datasource.password", POSTGRES.getPassword());
        properties.put("ALLOWED_ORIGINS", ORIGIN_SENTINEL);
        properties.put("PRIVACY_NOTICE_VERSION", "v1");
        properties.put("MINIMUM_APP_VERSION", "1.0.0");
        properties.put("sentinel.secret", SECRET_SENTINEL);
        return properties;
    }

    private static ConfigurableApplicationContext run(Map<String, Object> properties, String... profiles) {
        return new SpringApplicationBuilder(NeocatecumenalApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles(profiles)
                .properties(properties)
                .run();
    }

    private static String chainMessages(Throwable thrown) {
        List<String> messages = new ArrayList<>();
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            messages.add(String.valueOf(cause.getMessage()));
        }
        return String.join("\n", messages);
    }

    @Test
    void startsWithEveryRequiredSettingProvided() {
        try (ConfigurableApplicationContext context = run(productionProperties(), "prod")) {
            assertThat(context.isRunning()).isTrue();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "DATABASE_URL,spring.datasource.url",
            "ALLOWED_ORIGINS,identity.allowed-origins",
            "PRIVACY_NOTICE_VERSION,identity.privacy-notice-version",
            "MINIMUM_APP_VERSION,service.minimum-app-version"
    })
    void doesNotStartWhenARequiredSettingIsMissingAndNamesItWithoutValues(String variable, String property,
            CapturedOutput output) {
        Map<String, Object> properties = productionProperties();
        properties.remove(variable);

        assertThatThrownBy(() -> run(properties, "prod"))
                .satisfies(thrown -> {
                    String messages = chainMessages(thrown);
                    assertThat(messages).containsAnyOf(variable, property);
                    assertThat(messages).doesNotContain(SECRET_SENTINEL, ORIGIN_SENTINEL);
                });
        assertThat(output.getAll()).doesNotContain(SECRET_SENTINEL);
    }

    @Test
    void doesNotStartWithProductionTogetherWithLocal() {
        assertThatThrownBy(() -> run(productionProperties(), "prod", "local"))
                .satisfies(thrown -> assertThat(chainMessages(thrown)).contains("local"));
    }

    @Test
    void baseConfigurationCarriesNoDatabaseConnectionDefaults() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));

        assertThat(sources).isNotEmpty();
        for (PropertySource<?> source : sources) {
            assertThat(source.containsProperty("spring.datasource.url")).isFalse();
            assertThat(source.containsProperty("spring.datasource.username")).isFalse();
            assertThat(source.containsProperty("spring.datasource.password")).isFalse();
        }
    }
}
