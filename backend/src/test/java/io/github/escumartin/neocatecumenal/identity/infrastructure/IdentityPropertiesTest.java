package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.infrastructure.config.ConfiguredPrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.infrastructure.config.IdentityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class IdentityPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(IdentityProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void bootstrapIsDisabledWhenNothingIsConfigured() {
        runner.run(context -> assertThat(context.getBean(IdentityProperties.class).bootstrap().enabled()).isFalse());
    }

    @Test
    void bootstrapDataIsEmptyWhenNothingIsConfigured() {
        runner.run(context -> {
            IdentityProperties.Bootstrap bootstrap = context.getBean(IdentityProperties.class).bootstrap();
            assertThat(bootstrap.administratorName()).isNull();
            assertThat(bootstrap.administratorCommunity()).isNull();
        });
    }

    @Test
    void bootstrapDataIsBound() {
        runner.withPropertyValues(
                        "identity.bootstrap.administrator-name=Test Admin",
                        "identity.bootstrap.administrator-community=2")
                .run(context -> {
                    IdentityProperties.Bootstrap bootstrap = context.getBean(IdentityProperties.class).bootstrap();
                    assertThat(bootstrap.administratorName()).isEqualTo("Test Admin");
                    assertThat(bootstrap.administratorCommunity()).isEqualTo(2);
                });
    }

    @Test
    void bootstrapNoLongerAcceptsACommunityList() {
        assertThat(IdentityProperties.Bootstrap.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("enabled", "administratorName", "administratorCommunity");
    }

    @Test
    void allowedOriginsAreEmptyWhenNothingIsConfigured() {
        runner.run(context -> assertThat(context.getBean(IdentityProperties.class).allowedOrigins()).isEmpty());
    }

    @Test
    void configuredValuesAreBound() {
        runner.withPropertyValues(
                        "identity.privacy-notice-version=v7",
                        "identity.allowed-origins=https://app.example.test",
                        "identity.bootstrap.enabled=true")
                .run(context -> {
                    IdentityProperties properties = context.getBean(IdentityProperties.class);
                    assertThat(properties.privacyNoticeVersion()).isEqualTo("v7");
                    assertThat(properties.allowedOrigins()).containsExactly("https://app.example.test");
                    assertThat(properties.bootstrap().enabled()).isTrue();
                });
    }

    @Test
    void privacyNoticeSettingsExposeConfiguredVersion() {
        runner.withPropertyValues("identity.privacy-notice-version=v7").run(context -> {
            ConfiguredPrivacyNoticeSettings settings = new ConfiguredPrivacyNoticeSettings(
                    context.getBean(IdentityProperties.class));
            assertThat(settings.currentVersion()).isEqualTo("v7");
        });
    }
}
