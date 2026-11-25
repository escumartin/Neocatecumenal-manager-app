package io.github.escumartin.neocatecumenal.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RoomNameTest {

    @Test
    void keepsAValidName() {
        assertThat(new RoomName("Main hall").value()).isEqualTo("Main hall");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(new RoomName("  Main hall \t").value()).isEqualTo("Main hall");
    }

    @Test
    void acceptsASingleCharacter() {
        assertThat(new RoomName("A").value()).isEqualTo("A");
    }

    @Test
    void acceptsEightyCharacters() {
        assertThat(new RoomName("x".repeat(80)).value()).hasSize(80);
    }

    @Test
    void countsTheLengthAfterTrimming() {
        assertThat(new RoomName(" " + "x".repeat(80) + " ").value()).hasSize(80);
    }

    @Test
    void rejectsMoreThanEightyCharacters() {
        assertThatThrownBy(() -> new RoomName("x".repeat(81)))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("name");
                    assertThat(failure.code()).isEqualTo("TOO_LONG");
                });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   \t "})
    void rejectsAMissingOrBlankName(String value) {
        assertThatThrownBy(() -> new RoomName(value))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("name");
                    assertThat(failure.code()).isEqualTo("REQUIRED");
                });
    }

    @Test
    void comparesNamesIgnoringCase() {
        assertThat(new RoomName("Main Hall").sameAs(new RoomName("main hall"))).isTrue();
        assertThat(new RoomName("Main Hall").sameAs(new RoomName("Side hall"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc\u0000def", "abc\u0007", "\u001bboo", "line\nbreak", "tab\there"})
    void rejectsControlCharacters(String value) {
        assertThatThrownBy(() -> new RoomName(value))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("name");
                    assertThat(failure.code()).isEqualTo("INVALID_FORMAT");
                });
    }
}
