package io.github.escumartin.neocatecumenal.community.domain;

public class CommunityFieldInvalid extends CommunityDomainException {

    private final String field;

    public CommunityFieldInvalid(String field, String code) {
        super(code);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
