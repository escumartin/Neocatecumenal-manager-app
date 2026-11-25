package io.github.escumartin.neocatecumenal.identity;

public class IdentityRuleViolation extends RuntimeException {

    private final String code;

    public IdentityRuleViolation(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
