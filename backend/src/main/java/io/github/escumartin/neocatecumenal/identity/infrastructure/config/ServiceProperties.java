package io.github.escumartin.neocatecumenal.identity.infrastructure.config;

import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("service")
public record ServiceProperties(String minimumAppVersion, @DefaultValue("0000000") String revision) {

    private static final Pattern APP_VERSION =
            Pattern.compile("^(0|[1-9][0-9]{0,3})\\.(0|[1-9][0-9]{0,3})\\.(0|[1-9][0-9]{0,3})$");
    private static final Pattern REVISION = Pattern.compile("^[0-9a-f]{7,40}$");

    public ServiceProperties {
        if (minimumAppVersion == null || !APP_VERSION.matcher(minimumAppVersion).matches()) {
            throw new IllegalArgumentException("service.minimum-app-version debe tener el formato major.minor.patch");
        }
        if (revision == null || !REVISION.matcher(revision).matches()) {
            throw new IllegalArgumentException("service.revision debe ser un commit en hexadecimal de 7 a 40 caracteres");
        }
    }
}
