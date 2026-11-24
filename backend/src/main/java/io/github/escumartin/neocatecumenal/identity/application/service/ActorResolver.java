package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Actor;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.User;

final class ActorResolver {

    private final CurrentSessionContext currentSession;
    private final UserRepository users;
    private final PrivacyNoticeSettings privacyNotice;

    ActorResolver(CurrentSessionContext currentSession, UserRepository users, PrivacyNoticeSettings privacyNotice) {
        this.currentSession = currentSession;
        this.users = users;
        this.privacyNotice = privacyNotice;
    }

    Actor resolve() {
        User caller = currentSession.current()
                .flatMap(session -> users.findById(session.userId()))
                .orElseThrow(() -> new DomainException("UNAUTHENTICATED"));
        if (!caller.pendingSteps(privacyNotice.currentVersion()).isEmpty()) {
            throw new DomainException("FORBIDDEN");
        }
        return new Actor(caller.id(), caller.role(), caller.communityId());
    }
}
