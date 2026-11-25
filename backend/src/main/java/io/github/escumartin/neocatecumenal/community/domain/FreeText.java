package io.github.escumartin.neocatecumenal.community.domain;

final class FreeText {

    private FreeText() {
    }

    static void requireClean(String value, String field) {
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            if (isForbidden(codePoint)) {
                throw new CommunityFieldInvalid(field, "INVALID_FORMAT");
            }
            index += Character.charCount(codePoint);
        }
    }

    private static boolean isForbidden(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE;
    }
}
