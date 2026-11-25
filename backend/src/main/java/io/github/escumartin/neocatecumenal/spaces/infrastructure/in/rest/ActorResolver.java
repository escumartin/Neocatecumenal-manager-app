package io.github.escumartin.neocatecumenal.spaces.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.SpacesFieldInvalid;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.CurrentUser;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import io.github.escumartin.neocatecumenal.identity.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("spacesActorResolver")
public class ActorResolver {

    private final IdentityApi identity;

    public ActorResolver(IdentityApi identity) {
        this.identity = identity;
    }

    public Actor currentActor() {
        CurrentUser user = identity.currentUser().orElseThrow(UnauthenticatedException::new);
        return new Actor(new UserId(user.userId()), roleOf(user.role()), new CommunityId(user.communityId()));
    }

    public Optional<CommunityId> requestedCommunity(Actor actor, UUID communityId) {
        if (communityId == null) {
            return Optional.empty();
        }
        if (actor.role() == ActorRole.ADMINISTRATOR && identity.community(communityId).isEmpty()) {
            throw new SpacesFieldInvalid("communityId", "COMMUNITY_NOT_FOUND");
        }
        return Optional.of(new CommunityId(communityId));
    }

    private static ActorRole roleOf(Role role) {
        return switch (role) {
            case REGISTERED -> ActorRole.REGISTERED;
            case MODERATOR -> ActorRole.MODERATOR;
            case ADMINISTRATOR -> ActorRole.ADMINISTRATOR;
        };
    }
}
