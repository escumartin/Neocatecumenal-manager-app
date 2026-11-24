package io.github.escumartin.neocatecumenal.identity.domain;

import java.text.Normalizer;

public record DisplayName(String value) {

    public static final int MAX_LENGTH = 100;

    public DisplayName {
        if (value == null) {
            throw new DomainException("REQUIRED");
        }
        if (containsForbiddenCharacter(value)) {
            throw new DomainException("INVALID_FORMAT");
        }
        value = Normalizer.normalize(value, Normalizer.Form.NFC).strip();
        if (value.isEmpty()) {
            throw new DomainException("REQUIRED");
        }
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new DomainException("TOO_LONG");
        }
    }

    private static boolean containsForbiddenCharacter(String value) {
        int index = 0;
        while (index < value.length()) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index += 2;
                continue;
            }
            if (Character.isLowSurrogate(current) || Character.isISOControl(current) || isBidiOverride(current)) {
                return true;
            }
            index++;
        }
        return false;
    }

    private static boolean isBidiOverride(char character) {
        return (character >= '\u202a' && character <= '\u202e') || (character >= '\u2066' && character <= '\u2069');
    }
}
