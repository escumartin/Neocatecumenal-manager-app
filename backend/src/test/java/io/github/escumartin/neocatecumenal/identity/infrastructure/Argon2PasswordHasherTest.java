package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.security.Argon2PasswordHasher;
import org.junit.jupiter.api.Test;

class Argon2PasswordHasherTest {

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher();

    @Test
    void hashStartsWithAlgorithmPrefix() {
        PasswordHash hash = hasher.hash(new PlainPassword("correct horse"));

        assertThat(hash.value()).startsWith("{argon2}");
    }

    @Test
    void hashUsesConfiguredCostParameters() {
        PasswordHash hash = hasher.hash(new PlainPassword("correct horse"));

        assertThat(hash.value()).contains("$argon2id$v=19$m=19456,t=2,p=1$");
    }

    @Test
    void hashDoesNotContainThePassword() {
        PasswordHash hash = hasher.hash(new PlainPassword("correct horse"));

        assertThat(hash.value()).doesNotContain("correct horse");
    }

    @Test
    void sameInputProducesDifferentHashesBecauseOfSalt() {
        PlainPassword password = new PlainPassword("correct horse");

        assertThat(hasher.hash(password)).isNotEqualTo(hasher.hash(password));
    }

    @Test
    void matchesTheOriginalPassword() {
        PlainPassword password = new PlainPassword("correct horse");

        assertThat(hasher.matches(password, hasher.hash(password))).isTrue();
    }

    @Test
    void doesNotMatchAnotherPassword() {
        PasswordHash hash = hasher.hash(new PlainPassword("correct horse"));

        assertThat(hasher.matches(new PlainPassword("wrong horse"), hash)).isFalse();
    }

    @Test
    void matchesIgnoringUnicodeNormalizationForm() {
        PasswordHash hash = hasher.hash(new PlainPassword("café con leche"));

        assertThat(hasher.matches(new PlainPassword("café con leche"), hash)).isTrue();
    }

    @Test
    void doesNotMatchAMalformedHash() {
        assertThat(hasher.matches(new PlainPassword("correct horse"), new PasswordHash("{argon2}broken"))).isFalse();
        assertThat(hasher.matches(new PlainPassword("correct horse"), new PasswordHash("plain"))).isFalse();
    }

    @Test
    void dummyMatchNeverSucceeds() {
        assertThat(hasher.matchesDummy(new PlainPassword("correct horse"))).isFalse();
    }
}
