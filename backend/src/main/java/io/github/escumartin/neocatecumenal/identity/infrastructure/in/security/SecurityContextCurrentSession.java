package io.github.escumartin.neocatecumenal.identity.infrastructure.in.security;

import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSession;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCurrentSession implements CurrentSessionContext {

    @Override
    public Optional<CurrentSession> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof SessionAuthentication sessionAuthentication) {
            return Optional.of(sessionAuthentication.session());
        }
        return Optional.empty();
    }
}
