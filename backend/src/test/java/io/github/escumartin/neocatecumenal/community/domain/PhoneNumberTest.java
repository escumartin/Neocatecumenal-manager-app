package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumberTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "600 000 001|600000001",
            "600-000-301|600000301",
            "+351600000303|+351600000303",
            "+34 (600) 000.302|+34600000302",
            " 700000002 |700000002",
            "800000003|800000003",
            "900000004|900000004",
            "+12|+12"})
    void normalizesValidNumbers(String raw, String expected) {
        assertThat(PhoneNumber.of(raw).value()).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"512345678", "60000000", "6000000011", "+1234567890123456", "+0123456789", "abc",
            "600 000 00a", "+", "", "   "})
    void rejectsInvalidNumbers(String raw) {
        assertThatThrownBy(() -> PhoneNumber.of(raw)).isInstanceOfSatisfying(CommunityFieldInvalid.class,
                e -> assertThat(e.code()).isEqualTo("PHONE_INVALID"));
    }
}
