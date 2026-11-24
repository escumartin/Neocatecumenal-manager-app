package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.GetUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UserNotFoundException;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Actor;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserManagementPolicy;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import org.springframework.transaction.annotation.Transactional;

public class GetUserService implements GetUser {

    private final ActorResolver actors;
    private final UserRepository users;

    public GetUserService(CurrentSessionContext currentSession, UserRepository users,
            PrivacyNoticeSettings privacyNotice) {
        this.actors = new ActorResolver(currentSession, users, privacyNotice);
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public User get(UserId userId) {
        Actor actor = actors.resolve();
        if (actor.role() == UserRole.REGISTERED) {
            throw new DomainException("FORBIDDEN");
        }
        User target = users.findById(userId).orElseThrow(UserNotFoundException::new);
        if (!UserManagementPolicy.canList(actor, target.communityId())) {
            throw new DomainException("FORBIDDEN");
        }
        return target;
    }
}
