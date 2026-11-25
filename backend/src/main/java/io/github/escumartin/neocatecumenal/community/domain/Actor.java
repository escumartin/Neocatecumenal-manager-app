package io.github.escumartin.neocatecumenal.community.domain;

import java.util.Objects;

public record Actor(UserId id, ActorRole role, CommunityId communityId) {

    public Actor {
        Objects.requireNonNull(id);
        Objects.requireNonNull(role);
        Objects.requireNonNull(communityId);
    }
}
