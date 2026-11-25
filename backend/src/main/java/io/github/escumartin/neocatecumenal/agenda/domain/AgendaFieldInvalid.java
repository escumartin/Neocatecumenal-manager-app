package io.github.escumartin.neocatecumenal.agenda.domain;

public class AgendaFieldInvalid extends RuntimeException {

    private final String field;
    private final String code;

    public AgendaFieldInvalid(String field, String code) {
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
