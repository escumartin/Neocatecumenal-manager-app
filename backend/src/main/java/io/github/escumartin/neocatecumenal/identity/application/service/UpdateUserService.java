package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.FieldValidationException;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UpdateUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UpdateUserCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UserNotFoundException;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Actor;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserChanges;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class UpdateUserService implements UpdateUser {

    private static final Logger log = LoggerFactory.getLogger(UpdateUserService.class);

    private final ActorResolver actors;
    private final UserRepository users;
    private final CommunityRepository communities;

    public UpdateUserService(CurrentSessionContext currentSession, UserRepository users,
            CommunityRepository communities, PrivacyNoticeSettings privacyNotice) {
        this.actors = new ActorResolver(currentSession, users, privacyNotice);
        this.users = users;
        this.communities = communities;
    }

    @Override
    @Transactional
    public User update(UpdateUserCommand command) {
        Actor actor = actors.resolve();
        if (actor.role() == UserRole.REGISTERED) {
            throw new DomainException("FORBIDDEN");
        }
        User target = users.findById(command.userId()).orElseThrow(UserNotFoundException::new);
        target.update(actor, new UserChanges(command.name(), command.role(), command.communityId()),
                new LockingMembershipCounts(users));
        requireExisting(command.communityId());
        User saved = users.save(target);
        log.atInfo()
                .addKeyValue("actorId", actor.id().value())
                .addKeyValue("userId", saved.id().value())
                .log("Usuario actualizado");
        return saved;
    }

    private void requireExisting(CommunityId communityId) {
        if (communities.findById(communityId).isEmpty()) {
            throw new FieldValidationException("communityId", "COMMUNITY_NOT_FOUND");
        }
    }
}
