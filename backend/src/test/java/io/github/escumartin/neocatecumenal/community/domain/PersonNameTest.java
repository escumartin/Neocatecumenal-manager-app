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
}
