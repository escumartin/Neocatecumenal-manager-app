package io.github.escumartin.neocatecumenal.identity.application.port.in;

public class FieldValidationException extends RuntimeException {

    private final String field;
    private final String code;

    public FieldValidationException(String field, String code) {
        super(code);
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
