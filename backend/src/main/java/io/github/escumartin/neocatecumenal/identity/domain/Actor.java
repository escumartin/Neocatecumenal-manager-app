package io.github.escumartin.neocatecumenal.identity.domain;

public record Actor(UserId id, UserRole role, CommunityId communityId) {
}
