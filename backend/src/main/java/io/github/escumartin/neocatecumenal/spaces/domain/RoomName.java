package io.github.escumartin.neocatecumenal.spaces.domain;

public record RoomName(String value) {

    public static final int MAX_LENGTH = 80;

    public RoomName {
        if (value == null || value.isBlank()) {
            throw new SpacesFieldInvalid("name", "REQUIRED");
        }
        value = value.strip();
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new SpacesFieldInvalid("name", "INVALID_FORMAT");
        }
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new SpacesFieldInvalid("name", "TOO_LONG");
        }
    }

    public boolean sameAs(RoomName other) {
        return value.equalsIgnoreCase(other.value);
    }
}
