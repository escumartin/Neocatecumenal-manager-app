package io.github.escumartin.neocatecumenal.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValueObjectsTest {

    @ParameterizedTest
    @ValueSource(ints = {100000, 123456, 999999})
    void accessIdAcceptsSixDigitValues(int value) {
        assertThat(new AccessId(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 99999, 1000000})
    void accessIdRejectsValuesOutsideSixDigits(int value) {
        assertThatThrownBy(() -> new AccessId(value))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("INVALID_FORMAT");
    }

    @Test
    void communityNumberAcceptsPositiveValues() {
        assertThat(new CommunityNumber(1).value()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3})
    void communityNumberRejectsNonPositiveValues(int value) {
        assertThatThrownBy(() -> new CommunityNumber(value))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("INVALID_FORMAT");
    }

    @Test
    void displayNameIsTrimmed() {
        assertThat(new DisplayName("  Ana Pérez  ").value()).isEqualTo("Ana Pérez");
    }

    @Test
    void displayNameIsNormalizedToNfc() {
        assertThat(new DisplayName("José").value()).isEqualTo("José");
    }

    @Test
    void displayNameAcceptsExactlyOneHundredCharacters() {
        assertThat(new DisplayName("a".repeat(100)).value()).hasSize(100);
    }

    @Test
    void displayNameRejectsMoreThanOneHundredCharacters() {
        assertThatThrownBy(() -> new DisplayName("a".repeat(101)))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("TOO_LONG");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void displayNameRejectsBlankValues(String value) {
        assertThatThrownBy(() -> new DisplayName(value))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ana\nPerez", "Ana\u0007", "Ana\u0000", "\tAna", "Ana\u007f", "Ana\u202eevil",
            "\u202aAna", "Ana\u2066", "Ana\u2069", "Ana\ud800", "\udc00Ana", "Ana\ud83d"})
    void displayNameRejectsControlBidiOverrideAndLoneSurrogateCharacters(String value) {
        assertThatThrownBy(() -> new DisplayName(value))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("INVALID_FORMAT");
    }

    @Test
    void displayNameCountsSupplementaryCharactersAsOneCodePoint() {
        assertThat(new DisplayName("\ud83d\ude00".repeat(100)).value().codePointCount(0, 200)).isEqualTo(100);
        assertThatThrownBy(() -> new DisplayName("\ud83d\ude00".repeat(101)))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("TOO_LONG");
    }

    @Test
    void displayNameRejectsNull() {
        assertThatThrownBy(() -> new DisplayName(null))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("REQUIRED");
    }

    @Test
    void plainPasswordToStringIsMasked() {
        assertThat(new PlainPassword("s3cret-value").toString()).doesNotContain("s3cret-value");
    }

    @Test
    void plainPasswordIsNormalizedToNfc() {
        assertThat(new PlainPassword("café").value()).isEqualTo("café");
    }

    @Test
    void plainPasswordRejectsNull() {
        assertThatThrownBy(() -> new PlainPassword(null))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("REQUIRED");
    }

    @Test
    void privacyConsentKeepsVersionAndDate() {
        Instant accepted = Instant.parse("2026-01-01T10:00:00Z");

        PrivacyConsent consent = new PrivacyConsent("v1", accepted);

        assertThat(consent.noticeVersion()).isEqualTo("v1");
        assertThat(consent.acceptedAt()).isEqualTo(accepted);
    }

    @Test
    void privacyConsentRequiresVersion() {
        assertThatThrownBy(() -> new PrivacyConsent(null, Instant.now()))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("REQUIRED");
    }

    @Test
    void privacyConsentRequiresDate() {
        assertThatThrownBy(() -> new PrivacyConsent("v1", null))
                .isInstanceOf(DomainException.class)
                .extracting("code").isEqualTo("REQUIRED");
    }

    @Test
    void identifiersRejectNull() {
        assertThatThrownBy(() -> new UserId(null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new CommunityId(null)).isInstanceOf(DomainException.class);
        assertThat(new UserId(UUID.randomUUID()).value()).isNotNull();
    }
}
