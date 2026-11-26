package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.infrastructure.config.ServiceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ServicePropertiesTest {

    @Configuration
    @EnableConfigurationProperties(ServiceProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void minimumAppVersionAndRevisionAreBound() {
        runner.withPropertyValues("service.minimum-app-version=1.2.3", "service.revision=86a5aa6").run(context -> {
            ServiceProperties properties = context.getBean(ServiceProperties.class);
            assertThat(properties.minimumAppVersion()).isEqualTo("1.2.3");
            assertThat(properties.revision()).isEqualTo("86a5aa6");
        });
    }

    @Test
    void minimumAppVersionIsRequired() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "1.0", "1.0.0.0", "v1.0.0", "1.0.0-beta", "1.0.0+build", "01.0.0", "1.a.0",
            "10000.0.0"})
    void minimumAppVersionMustBeMajorMinorPatch(String value) {
        runner.withPropertyValues("service.minimum-app-version=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0", "1.0.0", "9999.9999.9999"})
    void minimumAppVersionAcceptsTheLimits(String value) {
        runner.withPropertyValues("service.minimum-app-version=" + value)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void revisionOutsideAnImageIsAllZeros() {
        runner.withPropertyValues("service.minimum-app-version=1.0.0")
                .run(context -> assertThat(context.getBean(ServiceProperties.class).revision()).isEqualTo("0000000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc12", "ABCDEF1", "86a5aa6g", "0123456789abcdef0123456789abcdef012345678"})
    void revisionMustBeAShortOrFullCommit(String value) {
        runner.withPropertyValues("service.minimum-app-version=1.0.0", "service.revision=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"86a5aa6", "0123456789abcdef0123456789abcdef01234567"})
    void revisionAcceptsTheLimits(String value) {
        runner.withPropertyValues("service.minimum-app-version=1.0.0", "service.revision=" + value)
                .run(context -> assertThat(context).hasNotFailed());
    }
}
