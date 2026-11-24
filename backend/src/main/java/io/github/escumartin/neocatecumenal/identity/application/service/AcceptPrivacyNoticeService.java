package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.AcceptPrivacyNotice;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class AcceptPrivacyNoticeService implements AcceptPrivacyNotice {

    private static final Logger log = LoggerFactory.getLogger(AcceptPrivacyNoticeService.class);

    private final CurrentSessionContext currentSession;
    private final UserRepository users;
    private final PrivacyNoticeSettings privacyNotice;
    private final Clock clock;

    public AcceptPrivacyNoticeService(CurrentSessionContext currentSession, UserRepository users,
            PrivacyNoticeSettings privacyNotice, Clock clock) {
        this.currentSession = currentSession;
        this.users = users;
        this.privacyNotice = privacyNotice;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void accept(String noticeVersion) {
        User user = currentSession.current()
                .flatMap(session -> users.findById(session.userId()))
                .orElseThrow(() -> new DomainException("UNAUTHENTICATED"));
        if (!privacyNotice.currentVersion().equals(noticeVersion)) {
            throw new DomainException("PRIVACY_NOTICE_OUTDATED");
        }
        user.acceptPrivacyNotice(noticeVersion, clock.instant());
        users.save(user);
        log.atInfo()
                .addKeyValue("userId", user.id().value())
                .addKeyValue("noticeVersion", noticeVersion)
                .log("Aviso de datos aceptado");
    }
}
