package io.github.escumartin.neocatecumenal.identity.infrastructure.in.security;

import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSession;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

final class SessionAuthentication extends AbstractAuthenticationToken {

    private final CurrentSession session;

    SessionAuthentication(CurrentSession session) {
        super(List.of());
        this.session = session;
        setAuthenticated(true);
    }

    CurrentSession session() {
        return session;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return session.userId().value();
    }
}
