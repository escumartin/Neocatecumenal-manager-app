package io.github.escumartin.neocatecumenal.agenda.domain;

import java.util.Objects;

public record Actor(UserId userId, ActorRole role, CommunityId ownCommunityId) {

    public Actor {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(role);
        Objects.requireNonNull(ownCommunityId);
    }
}
