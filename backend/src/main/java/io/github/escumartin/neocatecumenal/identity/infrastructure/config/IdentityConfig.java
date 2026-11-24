package io.github.escumartin.neocatecumenal.identity.infrastructure.config;

import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PrivacyNoticeSettings privacyNoticeSettings(IdentityProperties properties) {
        return new ConfiguredPrivacyNoticeSettings(properties);
    }
}
