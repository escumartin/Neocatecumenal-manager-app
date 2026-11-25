package io.github.escumartin.neocatecumenal.community.domain;

import java.util.Objects;

public record LinkedUser(UserId id, ActorRole role, CommunityId communityId, String name) {

    public LinkedUser {
        Objects.requireNonNull(id);
        Objects.requireNonNull(role);
        Objects.requireNonNull(communityId);
        Objects.requireNonNull(name);
    }
}
