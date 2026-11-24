package io.github.escumartin.neocatecumenal.identity.infrastructure.config;

import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;

public class ConfiguredPrivacyNoticeSettings implements PrivacyNoticeSettings {

    private final IdentityProperties properties;

    public ConfiguredPrivacyNoticeSettings(IdentityProperties properties) {
        this.properties = properties;
    }

    @Override
    public String currentVersion() {
        return properties.privacyNoticeVersion();
    }
}
