package io.github.escumartin.neocatecumenal.community.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record ContactChange(Map<ContactField, FieldChange> entries) {

    public ContactChange {
        Objects.requireNonNull(entries);
        EnumMap<ContactField, FieldChange> copy = new EnumMap<>(ContactField.class);
        copy.putAll(entries);
        entries = Map.copyOf(copy);
    }

    public static ContactChange of(Map<ContactField, FieldChange> entries) {
        return new ContactChange(entries);
    }

    public record FieldChange(boolean shared, String value) {

        public static FieldChange share(String value) {
            return new FieldChange(true, value);
        }

        public static FieldChange withdraw() {
            return new FieldChange(false, null);
        }
    }
}
