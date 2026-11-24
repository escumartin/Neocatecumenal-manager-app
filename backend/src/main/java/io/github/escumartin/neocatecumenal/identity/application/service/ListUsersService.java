package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.FieldValidationException;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ListUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ListUsersQuery;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UserPage;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Actor;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.UserManagementPolicy;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import org.springframework.transaction.annotation.Transactional;

public class ListUsersService implements ListUsers {

    private final ActorResolver actors;
    private final UserRepository users;
    private final CommunityRepository communities;

    public ListUsersService(CurrentSessionContext currentSession, UserRepository users,
            CommunityRepository communities, PrivacyNoticeSettings privacyNotice) {
        this.actors = new ActorResolver(currentSession, users, privacyNotice);
        this.users = users;
        this.communities = communities;
    }

    @Override
    @Transactional(readOnly = true)
    public UserPage list(ListUsersQuery query) {
        Actor actor = actors.resolve();
        if (actor.role() == UserRole.REGISTERED) {
            throw new DomainException("FORBIDDEN");
        }
        CommunityId communityId = communityOf(actor, query);
        if (!UserManagementPolicy.canList(actor, communityId)) {
            throw new DomainException("FORBIDDEN");
        }
        if (communities.findById(communityId).isEmpty()) {
            throw new FieldValidationException("communityId", "COMMUNITY_NOT_FOUND");
        }
        return users.findByCommunity(communityId, query.page(), query.size());
    }

    private static CommunityId communityOf(Actor actor, ListUsersQuery query) {
        return query.communityId().orElseGet(() -> {
            if (actor.role() == UserRole.MODERATOR) {
                return actor.communityId();
            }
            throw new FieldValidationException("communityId", "COMMUNITY_REQUIRED");
        });
    }
}
