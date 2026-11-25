package io.github.escumartin.neocatecumenal.agenda.domain;

public record EventTitle(String value) {

    public static final int MAX_LENGTH = 120;

    public EventTitle {
        if (value == null || value.isBlank()) {
            throw new AgendaFieldInvalid("title", "REQUIRED");
        }
        value = value.strip();
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new AgendaFieldInvalid("title", "INVALID_FORMAT");
        }
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new AgendaFieldInvalid("title", "TOO_LONG");
        }
    }
}
