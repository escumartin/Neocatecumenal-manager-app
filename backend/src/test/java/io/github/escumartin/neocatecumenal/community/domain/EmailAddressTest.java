package io.github.escumartin.neocatecumenal.community.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

    @ParameterizedTest
    @ValueSource(strings = {"ana@example.org", "ana.perez+parish@mail.example.org", "a_b-c@x-y.example.com", "o'neil@example.com"})
    void acceptsValidAddresses(String raw) {
        assertThat(EmailAddress.of(raw).value()).isEqualTo(raw);
    }

    @Test
    void trimsTheAddress() {
        assertThat(EmailAddress.of("  ana@example.org  ").value()).isEqualTo("ana@example.org");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"a@b", "ana", "@example.org", "ana@", "ana@@example.org", "ana@-example.org",
            "ana@example..org", "ana perez@example.org", "ana@example.org.", "", "   "})
    void rejectsInvalidAddresses(String raw) {
        assertThatThrownBy(() -> EmailAddress.of(raw)).isInstanceOfSatisfying(CommunityFieldInvalid.class,
                e -> assertThat(e.code()).isEqualTo("EMAIL_INVALID"));
    }

    @Test
    void acceptsAnAddressOfExactly254Characters() {
        String local = "a".repeat(64);
        String address = local + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(56) + ".test";

        assertThat(address).hasSize(254);
        assertThat(EmailAddress.of(address).value()).isEqualTo(address);
    }

    @Test
    void rejectsAnAddressOf255Characters() {
        String address = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(57) + ".test";

        assertThat(address).hasSize(255);
        assertThatThrownBy(() -> EmailAddress.of(address)).isInstanceOfSatisfying(CommunityFieldInvalid.class,
                e -> assertThat(e.code()).isEqualTo("EMAIL_INVALID"));
    }
}
