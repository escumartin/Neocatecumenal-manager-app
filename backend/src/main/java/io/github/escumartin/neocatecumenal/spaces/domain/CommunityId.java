package io.github.escumartin.neocatecumenal.spaces.domain;

import java.util.Objects;
import java.util.UUID;

public record CommunityId(UUID value) {

    public CommunityId {
        Objects.requireNonNull(value);
    }
}
