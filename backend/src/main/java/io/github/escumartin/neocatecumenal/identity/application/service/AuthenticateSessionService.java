package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.AuthenticateSession;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSession;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class AuthenticateSessionService implements AuthenticateSession {

    private final SessionRepository sessions;
    private final UserRepository users;
    private final SessionTokenIssuer tokens;
    private final Clock clock;

    public AuthenticateSessionService(SessionRepository sessions, UserRepository users, SessionTokenIssuer tokens,
            Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<CurrentSession> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return sessions.findByTokenHash(tokens.hash(token))
                .filter(session -> session.isValid(now))
                .flatMap(session -> users.findById(session.userId()).map(user -> {
                    renew(session, now);
                    return new CurrentSession(session.id(), user.id());
                }));
    }

    private void renew(Session session, Instant now) {
        if (session.needsTouch(now)) {
            sessions.save(session.touch(now));
        }
    }
}
