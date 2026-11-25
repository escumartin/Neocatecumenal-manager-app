package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CommunityNameTest {

    @Test
    void keepsAValidName() {
        assertThat(CommunityName.of("San Pedro")).hasValueSatisfying(name -> assertThat(name.value())
                .isEqualTo("San Pedro"));
    }

    @Test
    void stripsSurroundingWhitespace() {
        assertThat(CommunityName.of("  San Pedro \t")).hasValueSatisfying(name -> assertThat(name.value())
                .isEqualTo("San Pedro"));
    }

    @Test
    void normalizesToNfc() {
        assertThat(CommunityName.of("José")).hasValueSatisfying(name -> assertThat(name.value())
                .isEqualTo("José"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t\n"})
    void emptyOrBlankBecomesAbsent(String raw) {
        assertThat(CommunityName.of(raw)).isEmpty();
    }

    @Test
    void acceptsExactlyOneHundredCharacters() {
        assertThat(CommunityName.of("a".repeat(100))).isPresent();
    }

    @Test
    void rejectsOneHundredAndOneCharacters() {
        assertThatThrownBy(() -> CommunityName.of("a".repeat(101))).isInstanceOfSatisfying(
                CommunityFieldInvalid.class, e -> {
                    assertThat(e.field()).isEqualTo("name");
                    assertThat(e.code()).isEqualTo("TOO_LONG");
                });
    }

    @Test
    void lengthIsMeasuredAfterTrimmingAndNormalizing() {
        assertThat(CommunityName.of(" " + "é".repeat(100) + " ")).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"San\u0000Pedro", "San\nPedro", "San\rPedro", "San\u202ePedro", "San\u200bPedro",
            "San\ud800Pedro", "San\udc00Pedro", "San Pedro\ud83d"})
    void rejectsControlFormatAndLoneSurrogateCharacters(String raw) {
        assertThatThrownBy(() -> CommunityName.of(raw)).isInstanceOfSatisfying(CommunityFieldInvalid.class, e -> {
            assertThat(e.field()).isEqualTo("name");
            assertThat(e.code()).isEqualTo("INVALID_FORMAT");
        });
    }

    @Test
    void lengthIsCountedInCodePoints() {
        assertThat(CommunityName.of("\ud83d\ude00".repeat(100))).isPresent();
        assertThatThrownBy(() -> CommunityName.of("\ud83d\ude00".repeat(101))).isInstanceOfSatisfying(
                CommunityFieldInvalid.class, e -> assertThat(e.code()).isEqualTo("TOO_LONG"));
    }
}
