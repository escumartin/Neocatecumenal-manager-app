package io.github.escumartin.neocatecumenal.identity.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;

    private PasswordPolicy() {
    }

    public static List<String> check(PlainPassword password, AccessId accessId, CommonPasswords commonPasswords) {
        String value = password.value();
        int length = value.codePointCount(0, value.length());
        List<String> reasons = new ArrayList<>();
        if (isBlank(value) || containsControlCharacter(value)) {
            reasons.add("INVALID_FORMAT");
        }
        if (length < MIN_LENGTH) {
            reasons.add("PASSWORD_TOO_SHORT");
        }
        if (length > MAX_LENGTH) {
            reasons.add("PASSWORD_TOO_LONG");
        }
        if (commonPasswords.contains(value.toLowerCase(Locale.ROOT))) {
            reasons.add("PASSWORD_TOO_COMMON");
        }
        if (value.contains(String.valueOf(accessId.value()))) {
            reasons.add("PASSWORD_CONTAINS_ACCESS_ID");
        }
        return List.copyOf(reasons);
    }

    private static boolean isBlank(String value) {
        return value.codePoints().allMatch(codePoint -> Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint));
    }

    private static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
