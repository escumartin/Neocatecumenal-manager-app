package io.github.escumartin.neocatecumenal.community.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record PhoneNumber(String value) {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-()]");
    private static final Pattern VALID = Pattern.compile("^([6-9][0-9]{8}|\\+[1-9][0-9]{1,14})$");

    public PhoneNumber {
        Objects.requireNonNull(value);
    }

    public static PhoneNumber of(String raw) {
        String normalized = raw == null ? "" : SEPARATORS.matcher(raw).replaceAll("");
        if (!VALID.matcher(normalized).matches()) {
            throw new CommunityFieldInvalid("phone", "PHONE_INVALID");
        }
        return new PhoneNumber(normalized);
    }
}
