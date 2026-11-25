package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PersonNameTest {

    private static void assertInvalid(Runnable action, String field, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CommunityFieldInvalid.class, e -> {
            assertThat(e.field()).isEqualTo(field);
            assertThat(e.code()).isEqualTo(code);
        });
    }

    @Test
    void trimsBothParts() {
        PersonName name = PersonName.of("  Ana ", "  García López ");

        assertThat(name.givenName()).isEqualTo("Ana");
        assertThat(name.surnames()).contains("García López");
    }

    @Test
    void normalizesToNfc() {
        String decomposed = Normalizer.normalize("José", Normalizer.Form.NFD);

        assertThat(PersonName.of(decomposed, null).givenName()).isEqualTo("José");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void givenNameIsRequired(String given) {
        assertInvalid(() -> PersonName.of(given, "Pérez"), "givenName", "REQUIRED");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void blankSurnamesAreAbsent(String surnames) {
        assertThat(PersonName.of("Ana", surnames).surnames()).isEmpty();
    }

    @Test
    void acceptsOneHundredCharacters() {
        assertThat(PersonName.of("a".repeat(100), "b".repeat(100)).givenName()).hasSize(100);
    }

    @Test
    void rejectsAGivenNameOfOneHundredAndOneCharacters() {
        assertInvalid(() -> PersonName.of("a".repeat(101), null), "givenName", "TOO_LONG");
    }

    @Test
    void rejectsSurnamesOfOneHundredAndOneCharacters() {
        assertInvalid(() -> PersonName.of("Ana", "b".repeat(101)), "surnames", "TOO_LONG");
    }

    @ParameterizedTest
    @ValueSource(strings = {"An\u0000a", "An\na", "An\ra", "An\ta", "An\u202ea", "An\u2066a", "An\u200ba",
            "An\ud800a", "An\udc00a", "Ana\ud83d"})
    void rejectsControlFormatAndLoneSurrogateCharactersInTheGivenName(String given) {
        assertInvalid(() -> PersonName.of(given, null), "givenName", "INVALID_FORMAT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pe\u0000rez", "Pe\nrez", "Pe\u202erez", "Pe\udc00rez"})
    void rejectsControlFormatAndLoneSurrogateCharactersInTheSurnames(String surnames) {
        assertInvalid(() -> PersonName.of("Ana", surnames), "surnames", "INVALID_FORMAT");
    }

    @Test
    void acceptsAnAstralCharacterAsOneCodePoint() {
        assertThat(PersonName.of("\ud83d\ude00".repeat(100), null).givenName()).isNotEmpty();
        assertInvalid(() -> PersonName.of("\ud83d\ude00".repeat(101), null), "givenName", "TOO_LONG");
    }
}
