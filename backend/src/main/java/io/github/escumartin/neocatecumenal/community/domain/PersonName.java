package io.github.escumartin.neocatecumenal.community.domain;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;

public record PersonName(String givenName, Optional<String> surnames) {

    public static final int MAX_LENGTH = 100;

    public PersonName {
        Objects.requireNonNull(givenName);
        Objects.requireNonNull(surnames);
    }

    public static PersonName of(String givenName, String surnames) {
        String given = normalize(givenName);
        if (given.isEmpty()) {
            throw new CommunityFieldInvalid("givenName", "REQUIRED");
        }
        requireWithinLimit(given, "givenName");
        String family = normalize(surnames);
        requireWithinLimit(family, "surnames");
        return new PersonName(given, family.isEmpty() ? Optional.empty() : Optional.of(family));
    }

    private static String normalize(String raw) {
        return raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
    }

    private static void requireWithinLimit(String value, String field) {
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new CommunityFieldInvalid(field, "TOO_LONG");
        }
    }
}
