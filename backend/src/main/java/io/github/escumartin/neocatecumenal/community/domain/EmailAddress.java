package io.github.escumartin.neocatecumenal.community.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record EmailAddress(String value) {

    public static final int MAX_LENGTH = 254;

    private static final Pattern VALID = Pattern.compile(
            "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"
                    + "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$");

    public EmailAddress {
        Objects.requireNonNull(value);
    }

    public static EmailAddress of(String raw) {
        String trimmed = raw == null ? "" : raw.strip();
        if (trimmed.length() > MAX_LENGTH || !VALID.matcher(trimmed).matches()) {
            throw new CommunityFieldInvalid("email", "EMAIL_INVALID");
        }
        return new EmailAddress(trimmed);
    }
}
