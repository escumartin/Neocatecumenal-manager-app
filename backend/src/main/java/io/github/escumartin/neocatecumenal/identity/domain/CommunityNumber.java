package io.github.escumartin.neocatecumenal.identity.domain;

public record CommunityNumber(int value) {

    public CommunityNumber {
        if (value <= 0) {
            throw new DomainException("INVALID_FORMAT");
        }
    }
}
