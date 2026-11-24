package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.LogOut;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class LogOutService implements LogOut {

    private static final Logger log = LoggerFactory.getLogger(LogOutService.class);

    private final CurrentSessionContext currentSession;
    private final SessionRepository sessions;

    public LogOutService(CurrentSessionContext currentSession, SessionRepository sessions) {
        this.currentSession = currentSession;
        this.sessions = sessions;
    }

    @Override
    @Transactional
    public void logOut() {
        currentSession.current().ifPresent(session -> {
            sessions.delete(session.sessionId());
            log.atInfo().addKeyValue("userId", session.userId().value()).log("Sesión cerrada");
        });
    }
}
