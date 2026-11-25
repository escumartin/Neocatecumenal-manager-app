package io.github.escumartin.neocatecumenal.community.domain;

import java.text.Normalizer;
import java.util.Optional;

public record CommunityName(String value) {

    public static final int MAX_LENGTH = 100;

    public CommunityName {
        if (value == null || value.isBlank()) {
            throw new CommunityFieldInvalid("name", "REQUIRED");
        }
        FreeText.requireClean(value, "name");
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            throw new CommunityFieldInvalid("name", "TOO_LONG");
        }
    }

    public static Optional<CommunityName> of(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CommunityName(normalized));
    }
}
