package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import io.github.escumartin.neocatecumenal.identity.UserLinkGuard;
import io.github.escumartin.neocatecumenal.identity.application.port.in.FieldValidationException;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UpdateUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UpdateUserCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UserNotFoundException;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Actor;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
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
    private final UserLinkGuard guard;

    public UpdateUserService(CurrentSessionContext currentSession, UserRepository users,
            CommunityRepository communities, PrivacyNoticeSettings privacyNotice, UserLinkGuard guard) {
        this.actors = new ActorResolver(currentSession, users, privacyNotice);
        this.users = users;
        this.communities = communities;
        this.guard = guard;
    }

    @Override
    @Transactional
    public User update(UpdateUserCommand command) {
        Actor actor = actors.resolve();
        if (actor.role() == UserRole.REGISTERED) {
            throw new DomainException("FORBIDDEN");
        }
        User target = users.findById(command.userId()).orElseThrow(UserNotFoundException::new);
        boolean communityChanges = !command.communityId().equals(target.communityId());
        if (communityChanges) {
            target = lockedAfterGuard(target, command.communityId());
        }
        UserChanges changes = new UserChanges(command.name(), command.role(), command.communityId());
        target.checkUpdate(actor, changes, new LockingMembershipCounts(users));
        requireExisting(command.communityId(), communityChanges);
        target.apply(changes);
        User saved = users.save(target);
        log.atInfo()
                .addKeyValue("actorId", actor.id().value())
                .addKeyValue("userId", saved.id().value())
                .log("Usuario actualizado");
        return saved;
    }

    private User lockedAfterGuard(User target, CommunityId destination) {
        if (!guard.allowsCommunityChange(target.id().value(), target.communityId().value(), destination.value())) {
            throw new DomainException("USER_LINKED_TO_MEMBER");
        }
        User reread = users.findByIdForUpdate(target.id()).orElseThrow(UserNotFoundException::new);
        if (!reread.communityId().equals(target.communityId())) {
            throw new StaleLockOrderException();
        }
        lockCommunitiesInOrder(reread.communityId(), destination);
        return reread;
    }

    private void lockCommunitiesInOrder(CommunityId first, CommunityId second) {
        if (first.value().compareTo(second.value()) <= 0) {
            users.lockCommunity(first);
            users.lockCommunity(second);
        } else {
            users.lockCommunity(second);
            users.lockCommunity(first);
        }
    }

    private void requireExisting(CommunityId communityId, boolean communityChanges) {
        Community community = communities.findById(communityId)
                .orElseThrow(() -> new FieldValidationException("communityId", "COMMUNITY_NOT_FOUND"));
        if (communityChanges && !community.active()) {
            throw new FieldValidationException("communityId", "COMMUNITY_DEACTIVATED");
        }
    }
}
