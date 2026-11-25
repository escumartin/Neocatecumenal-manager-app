package io.github.escumartin.neocatecumenal.spaces.domain;

public class SpacesFieldInvalid extends RuntimeException {

    private final String field;
    private final String code;

    public SpacesFieldInvalid(String field, String code) {
        this.field = field;
        this.code = code;
    }

    public String field() {
        return field;
    }

    public String code() {
        return code;
    }
}
