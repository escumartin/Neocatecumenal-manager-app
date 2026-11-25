package io.github.escumartin.neocatecumenal.community.domain;

public record CommunityNumber(int value) {

    public CommunityNumber {
        if (value <= 0) {
            throw new CommunityFieldInvalid("number", "INVALID_FORMAT");
        }
    }
}
