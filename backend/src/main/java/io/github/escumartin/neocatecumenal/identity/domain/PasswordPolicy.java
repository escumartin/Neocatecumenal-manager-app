package io.github.escumartin.neocatecumenal.identity.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

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
        if (isCommon(value, commonPasswords)) {
            reasons.add("PASSWORD_TOO_COMMON");
        }
        if (value.contains(String.valueOf(accessId.value()))) {
            reasons.add("PASSWORD_CONTAINS_ACCESS_ID");
        }
        return List.copyOf(reasons);
    }

    private static boolean isCommon(String value, CommonPasswords commonPasswords) {
        String lowerCase = value.toLowerCase(Locale.ROOT);
        String bare = stripLeadingNonLetters(stripTrailingNonLetters(lowerCase));
        return Stream.of(lowerCase, stripTrailingNonLetters(lowerCase), stripLeadingNonLetters(lowerCase), bare)
                .flatMap(candidate -> Stream.of(candidate, substituteLeet(candidate, 'i'),
                        substituteLeet(candidate, 'l')))
                .filter(candidate -> !candidate.isEmpty())
                .anyMatch(commonPasswords::contains);
    }

    private static String stripTrailingNonLetters(String value) {
        int end = value.length();
        while (end > 0) {
            int codePoint = value.codePointBefore(end);
            if (Character.isLetter(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }

    private static String stripLeadingNonLetters(String value) {
        int start = 0;
        while (start < value.length()) {
            int codePoint = value.codePointAt(start);
            if (Character.isLetter(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        return value.substring(start);
    }

    private static String substituteLeet(String value, char one) {
        StringBuilder result = new StringBuilder(value.length());
        value.chars().forEach(character -> result.append(switch (character) {
            case '@', '4' -> 'a';
            case '0' -> 'o';
            case '1' -> one;
            case '3' -> 'e';
            case '5', '$' -> 's';
            case '7' -> 't';
            default -> (char) character;
        }));
        return result.toString();
    }

    private static boolean isBlank(String value) {
        return value.codePoints().allMatch(codePoint -> Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint));
    }

    private static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
