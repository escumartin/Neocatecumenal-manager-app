package io.github.escumartin.neocatecumenal.identity.domain;

import java.util.UUID;

public record CommunityId(UUID value) {

    public CommunityId {
        if (value == null) {
            throw new DomainException("REQUIRED");
        }
    }
}
