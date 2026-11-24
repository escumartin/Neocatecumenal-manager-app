package io.github.escumartin.neocatecumenal.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private static final AccessId ACCESS_ID = new AccessId(123456);
    private static final Set<String> COMMON = Set.of("password1", "contraseña");
    private static final CommonPasswords COMMONS = COMMON::contains;

    private static List<String> check(String password) {
        return PasswordPolicy.check(new PlainPassword(password), ACCESS_ID, COMMONS);
    }

    @Test
    void acceptsAPasswordOfEightCodePoints() {
        assertThat(check("tr4nquil")).isEmpty();
    }

    @Test
    void rejectsSevenCodePointsAsTooShort() {
        assertThat(check("tr4nqui")).containsExactly("PASSWORD_TOO_SHORT");
    }

    @Test
    void acceptsSixtyFourCodePoints() {
        assertThat(check("a".repeat(64))).isEmpty();
    }

    @Test
    void rejectsSixtyFiveCodePointsAsTooLong() {
        assertThat(check("a".repeat(65))).containsExactly("PASSWORD_TOO_LONG");
    }

    @Test
    void countsAccentedCharactersAsOneCodePointEach() {
        assertThat(check("ñ".repeat(64))).isEmpty();
        assertThat(check("é".repeat(65))).containsExactly("PASSWORD_TOO_LONG");
    }

    @Test
    void countsDecomposedAccentsAfterNormalisation() {
        String decomposed = "é".repeat(64);

        assertThat(check(decomposed)).isEmpty();
    }

    @Test
    void countsSupplementaryCharactersAsOneCodePoint() {
        assertThat(check("😀".repeat(8))).isEmpty();
        assertThat(check("😀".repeat(7))).containsExactly("PASSWORD_TOO_SHORT");
    }

    @Test
    void rejectsAWhitespaceOnlyPasswordAsInvalidFormat() {
        assertThat(check(" ".repeat(10))).containsExactly("INVALID_FORMAT");
        assertThat(check(" \t \u00a0 \u2003  ")).containsExactly("INVALID_FORMAT");
    }

    @Test
    void rejectsControlCharactersAsInvalidFormat() {
        assertThat(check("abcd\u0000efgh")).containsExactly("INVALID_FORMAT");
        assertThat(check("abcd\nefgh")).containsExactly("INVALID_FORMAT");
        assertThat(check("abcd\u007fefgh")).containsExactly("INVALID_FORMAT");
    }

    @Test
    void acceptsAPasswordWithInnerSpaces() {
        assertThat(check("correct horse battery")).isEmpty();
    }

    @Test
    void rejectsACommonPasswordIgnoringCase() {
        assertThat(check("PassWord1")).containsExactly("PASSWORD_TOO_COMMON");
        assertThat(check("CONTRASEÑA")).containsExactly("PASSWORD_TOO_COMMON");
    }

    @Test
    void rejectsAPasswordContainingTheAccessId() {
        assertThat(check("xx123456xx")).containsExactly("PASSWORD_CONTAINS_ACCESS_ID");
    }

    @Test
    void acceptsAPasswordWithADifferentAccessId() {
        assertThat(check("xx654321xx")).isEmpty();
    }

    @Test
    void reportsEveryReasonInAFixedOrder() {
        CommonPasswords everythingIsCommon = password -> true;

        assertThat(PasswordPolicy.check(new PlainPassword("abcdefg"), ACCESS_ID, everythingIsCommon))
                .containsExactly("PASSWORD_TOO_SHORT", "PASSWORD_TOO_COMMON");
        assertThat(PasswordPolicy.check(new PlainPassword("a123456a".repeat(9)), ACCESS_ID, everythingIsCommon))
                .containsExactly("PASSWORD_TOO_LONG", "PASSWORD_TOO_COMMON", "PASSWORD_CONTAINS_ACCESS_ID");
    }
}
