package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.RejectPrivacyNotice;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSession;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class RejectPrivacyNoticeService implements RejectPrivacyNotice {

    private static final Logger log = LoggerFactory.getLogger(RejectPrivacyNoticeService.class);

    private final CurrentSessionContext currentSession;
    private final UserRepository users;
    private final SessionRepository sessions;

    public RejectPrivacyNoticeService(CurrentSessionContext currentSession, UserRepository users,
            SessionRepository sessions) {
        this.currentSession = currentSession;
        this.users = users;
        this.sessions = sessions;
    }

    @Override
    @Transactional
    public void reject() {
        CurrentSession session = currentSession.current().orElseThrow(() -> new DomainException("UNAUTHENTICATED"));
        users.findById(session.userId()).ifPresent(user -> {
            user.withdrawPrivacyConsent();
            users.save(user);
        });
        sessions.delete(session.sessionId());
        log.atInfo().addKeyValue("userId", session.userId().value()).log("Aviso de datos rechazado, sesión cerrada");
    }
}
