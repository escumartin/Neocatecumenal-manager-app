package io.github.escumartin.neocatecumenal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationGuardTest {

    private static final String SECRET_LIKE_ORIGIN = "https://secret-host.example.test";

    private static Map<String, String> productionProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("identity.allowed-origins[0]", SECRET_LIKE_ORIGIN);
        properties.put("spring.datasource.url", "jdbc:postgresql://db.example.test/app");
        properties.put("identity.privacy-notice-version", "v1");
        properties.put("service.minimum-app-version", "1.0.0");
        return properties;
    }

    private static ProductionConfigurationGuard guardWith(Map<String, String> properties, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        properties.forEach(environment::setProperty);
        ProductionConfigurationGuard guard = new ProductionConfigurationGuard();
        guard.setEnvironment(environment);
        return guard;
    }

    @Test
    void acceptsProductionWithAllRequiredProperties() {
        ProductionConfigurationGuard guard = guardWith(productionProperties(), "prod");

        assertThatCode(guard::verify).doesNotThrowAnyException();
    }

    @Test
    void rejectsProductionTogetherWithLocalProfile() {
        ProductionConfigurationGuard guard = guardWith(productionProperties(), "prod", "local");

        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local");
    }

    @Test
    void rejectsMissingAllowedOrigins() {
        Map<String, String> properties = productionProperties();
        properties.remove("identity.allowed-origins[0]");
        ProductionConfigurationGuard guard = guardWith(properties, "prod");

        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity.allowed-origins");
    }

    @Test
    void rejectsEmptyAllowedOriginsValue() {
        Map<String, String> properties = productionProperties();
        properties.remove("identity.allowed-origins[0]");
        properties.put("identity.allowed-origins", "");
        ProductionConfigurationGuard guard = guardWith(properties, "prod");

        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity.allowed-origins");
    }

    @Test
    void rejectsBlankAllowedOrigin() {
        Map<String, String> properties = productionProperties();
        properties.put("identity.allowed-origins[0]", "  ");
        ProductionConfigurationGuard guard = guardWith(properties, "prod");

        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity.allowed-origins");
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.url", "identity.privacy-notice-version",
            "service.minimum-app-version"})
    void rejectsMissingRequiredProperty(String property) {
        Map<String, String> properties = productionProperties();
        properties.remove(property);
        ProductionConfigurationGuard guard = guardWith(properties, "prod");

        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(property);
    }

    @Test
    void rejectsUnresolvedPlaceholder() {
        Map<String, String> properties = productionProperties();
        properties.put("service.minimum-app-version", "${MINIMUM_APP_VERSION}");
        ProductionConfigurationGuard guard = guardWith(properties, "prod");

        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("service.minimum-app-version");
    }

    @Test
    void rejectsUnresolvedPlaceholderInAllowedOrigins() {
        Map<String, String> properties = productionProperties();
        properties.remove("identity.allowed-origins[0]");
        properties.put("identity.allowed-origins", "${ALLOWED_ORIGINS}");
        ProductionConfigurationGuard guard = guardWith(properties, "prod");

        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity.allowed-origins");
    }

    @Test
    void messageNamesThePropertyButNeverItsValue() {
        ProductionConfigurationGuard guard = guardWith(productionProperties(), "prod", "local");

        assertThatThrownBy(guard::verify)
                .satisfies(thrown -> assertThat(thrown.getMessage()).doesNotContain(SECRET_LIKE_ORIGIN));
    }

    @Test
    void messageForBlankOriginNeverShowsOtherOrigins() {
        Map<String, String> properties = productionProperties();
        properties.put("identity.allowed-origins[1]", " ");
        ProductionConfigurationGuard guard = guardWith(properties, "prod");

        assertThatThrownBy(guard::verify)
                .satisfies(thrown -> assertThat(thrown.getMessage()).doesNotContain(SECRET_LIKE_ORIGIN));
    }
}
