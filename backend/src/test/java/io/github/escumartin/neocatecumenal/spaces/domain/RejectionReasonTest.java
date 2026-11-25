package io.github.escumartin.neocatecumenal.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RejectionReasonTest {

    @Test
    void keepsAValidReason() {
        assertThat(new RejectionReason("Hall under maintenance").value()).isEqualTo("Hall under maintenance");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(new RejectionReason("  Hall under maintenance \t").value()).isEqualTo("Hall under maintenance");
    }

    @Test
    void acceptsASingleCharacter() {
        assertThat(new RejectionReason("A").value()).isEqualTo("A");
    }

    @Test
    void acceptsFiveHundredCharacters() {
        assertThat(new RejectionReason("x".repeat(500)).value()).hasSize(500);
    }

    @Test
    void countsTheLengthAfterTrimming() {
        assertThat(new RejectionReason(" " + "x".repeat(500) + " ").value()).hasSize(500);
    }

    @Test
    void rejectsMoreThanFiveHundredCharacters() {
        assertThatThrownBy(() -> new RejectionReason("x".repeat(501)))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("reason");
                    assertThat(failure.code()).isEqualTo("TOO_LONG");
                });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   \t "})
    void rejectsAMissingOrBlankReason(String value) {
        assertThatThrownBy(() -> new RejectionReason(value))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("reason");
                    assertThat(failure.code()).isEqualTo("REQUIRED");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc\u0000def", "abc\u0007", "\u001bboo", "line\nbreak", "tab\there"})
    void rejectsControlCharacters(String value) {
        assertThatThrownBy(() -> new RejectionReason(value))
                .isInstanceOfSatisfying(SpacesFieldInvalid.class, failure -> {
                    assertThat(failure.field()).isEqualTo("reason");
                    assertThat(failure.code()).isEqualTo("INVALID_FORMAT");
                });
    }
}
