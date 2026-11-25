package io.github.escumartin.neocatecumenal.community.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.CurrentUser;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import org.springframework.stereotype.Component;

@Component("communityActorResolver")
public class ActorResolver {

    private final IdentityApi identity;

    public ActorResolver(IdentityApi identity) {
        this.identity = identity;
    }

    public Actor currentActor() {
        CurrentUser user = identity.currentUser().orElseThrow(() -> new CommunityDomainException("UNAUTHENTICATED"));
        return new Actor(new UserId(user.userId()), ActorRole.valueOf(user.role().name()),
                new CommunityId(user.communityId()));
    }
}
