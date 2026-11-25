package io.github.escumartin.neocatecumenal.community.domain;

public class CommunityDomainException extends RuntimeException {

    private final String code;

    public CommunityDomainException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
