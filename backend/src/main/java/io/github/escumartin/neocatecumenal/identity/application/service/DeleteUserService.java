package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import io.github.escumartin.neocatecumenal.identity.UserDeleted;
import io.github.escumartin.neocatecumenal.identity.UserLinkGuard;
import io.github.escumartin.neocatecumenal.identity.application.port.in.DeleteUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UserNotFoundException;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.IdentityEventPublisher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Actor;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserManagementPolicy;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class DeleteUserService implements DeleteUser {

    private static final Logger log = LoggerFactory.getLogger(DeleteUserService.class);

    private final ActorResolver actors;
    private final UserRepository users;
    private final SessionRepository sessions;
    private final IdentityEventPublisher events;
    private final UserLinkGuard guard;

    public DeleteUserService(CurrentSessionContext currentSession, UserRepository users,
            SessionRepository sessions, IdentityEventPublisher events, PrivacyNoticeSettings privacyNotice,
            UserLinkGuard guard) {
        this.actors = new ActorResolver(currentSession, users, privacyNotice);
        this.users = users;
        this.sessions = sessions;
        this.events = events;
        this.guard = guard;
    }

    @Override
    @Transactional
    public void delete(UserId userId) {
        Actor actor = actors.resolve();
        if (actor.role() == UserRole.REGISTERED) {
            throw new DomainException("FORBIDDEN");
        }
        User target = lockedAfterGuard(users.findById(userId).orElseThrow(UserNotFoundException::new));
        UserManagementPolicy.checkDeletion(actor, target, new LockingMembershipCounts(users));
        sessions.deleteAllOf(target.id());
        users.delete(target.id());
        events.publish(new UserDeleted(target.id().value()));
        log.atInfo()
                .addKeyValue("actorId", actor.id().value())
                .addKeyValue("userId", target.id().value())
                .log("Usuario borrado");
    }

    private User lockedAfterGuard(User target) {
        guard.releaseBeforeDeletion(target.id().value(), target.communityId().value());
        User reread = users.findByIdForUpdate(target.id()).orElseThrow(UserNotFoundException::new);
        if (!reread.communityId().equals(target.communityId())) {
            throw new StaleLockOrderException();
        }
        return reread;
    }
}
