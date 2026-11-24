package io.github.escumartin.neocatecumenal.identity.domain;

import java.text.Normalizer;

public record PlainPassword(String value) {

    public PlainPassword {
        if (value == null) {
            throw new DomainException("REQUIRED");
        }
        value = Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    @Override
    public String toString() {
        return "PlainPassword[****]";
    }
}
