package io.github.escumartin.neocatecumenal.identity.infrastructure.in.security;

import io.github.escumartin.neocatecumenal.identity.infrastructure.config.IdentityProperties;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class OriginCheck {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> allowedOrigins;

    public OriginCheck(IdentityProperties properties) {
        this.allowedOrigins = Set.copyOf(properties.allowedOrigins());
    }

    public boolean changesState(String method) {
        return !SAFE_METHODS.contains(method.toUpperCase());
    }

    public boolean allows(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

    public void require(String origin) {
        if (!allows(origin)) {
            throw new OriginNotAllowedException();
        }
    }
}
