package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommunityNumberTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 12, Integer.MAX_VALUE})
    void acceptsPositiveNumbers(int value) {
        assertThat(new CommunityNumber(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsZeroAndNegativeNumbers(int value) {
        assertThatThrownBy(() -> new CommunityNumber(value)).isInstanceOfSatisfying(CommunityFieldInvalid.class, e -> {
            assertThat(e.field()).isEqualTo("number");
            assertThat(e.code()).isEqualTo("INVALID_FORMAT");
        });
    }

    @Test
    void equalNumbersAreEqual() {
        assertThat(new CommunityNumber(7)).isEqualTo(new CommunityNumber(7));
    }
}
