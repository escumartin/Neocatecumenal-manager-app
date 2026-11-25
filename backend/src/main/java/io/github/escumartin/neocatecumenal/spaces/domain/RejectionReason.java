package io.github.escumartin.neocatecumenal.spaces.domain;

public record RejectionReason(String value) {

    public static final int MAX_LENGTH = 500;

    public RejectionReason {
        if (value == null || value.isBlank()) {
            throw new SpacesFieldInvalid("reason", "REQUIRED");
        }
        value = value.strip();
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new SpacesFieldInvalid("reason", "INVALID_FORMAT");
        }
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new SpacesFieldInvalid("reason", "TOO_LONG");
        }
    }
}
