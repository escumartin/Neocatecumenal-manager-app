package io.github.escumartin.neocatecumenal.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReasonTest {

    @Test
    void keepsAValidReason() {
        assertThat(new Reason("Weekly catechesis").value()).isEqualTo("Weekly catechesis");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(new Reason("  Weekly catechesis \t").value()).isEqualTo("Weekly catechesis");
    }

    @Test
    void acceptsASingleCharacter() {
        assertThat(new Reason("A").value()).isEqualTo("A");
    }

    @Test
    void acceptsFiveHundredCharacters() {
        assertThat(new Reason("x".repeat(500)).value()).hasSize(500);
    }

    @Test
    void countsTheLengthAfterTrimming() {
        assertThat(new Reason(" " + "x".repeat(500) + " ").value()).hasSize(500);
    }

    @Test
    void rejectsMoreThanFiveHundredCharacters() {
        assertThatThrownBy(() -> new Reason("x".repeat(501)))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("reason");
                    assertThat(failure.code()).isEqualTo("TOO_LONG");
                });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   \t "})
    void rejectsAMissingOrBlankReason(String value) {
        assertThatThrownBy(() -> new Reason(value))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("reason");
                    assertThat(failure.code()).isEqualTo("REQUIRED");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc\u0000def", "abc\u0007", "\u001bboo", "line\nbreak", "tab\there"})
    void rejectsControlCharacters(String value) {
        assertThatThrownBy(() -> new Reason(value))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("reason");
                    assertThat(failure.code()).isEqualTo("INVALID_FORMAT");
                });
    }
}
