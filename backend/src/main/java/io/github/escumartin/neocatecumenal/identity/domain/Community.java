package io.github.escumartin.neocatecumenal.identity.domain;

public record Community(CommunityId id, CommunityNumber number, boolean active) {

    public Community(CommunityId id, CommunityNumber number) {
        this(id, number, true);
    }
}
