package io.github.escumartin.neocatecumenal.identity.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("identity")
public record IdentityProperties(
        @DefaultValue("v1") String privacyNoticeVersion,
        @DefaultValue List<String> allowedOrigins,
        @DefaultValue Bootstrap bootstrap) {

    public record Bootstrap(
            @DefaultValue("false") boolean enabled,
            String administratorName,
            Integer administratorCommunity) {
    }
}
