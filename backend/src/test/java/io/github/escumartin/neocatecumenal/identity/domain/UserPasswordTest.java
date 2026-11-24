package io.github.escumartin.neocatecumenal.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserPasswordTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    private static User user(boolean passwordChangeRequired, PrivacyConsent consent) {
        return User.restore(new UserId(UUID.randomUUID()), new AccessId(123456), new DisplayName("Test User"),
                UserRole.REGISTERED, new CommunityId(UUID.randomUUID()), new PasswordHash("{argon2}old"),
                passwordChangeRequired, consent, 4L);
    }

    @Test
    void resetPasswordStoresTheInitialHashAndRequiresAChange() {
        User user = user(false, new PrivacyConsent("v2", NOW));

        user.resetPassword(new PasswordHash("{argon2}initial"));

        assertThat(user.passwordHash()).isEqualTo(new PasswordHash("{argon2}initial"));
        assertThat(user.passwordChangeRequired()).isTrue();
        assertThat(user.pendingSteps("v2")).containsExactly(PendingStep.PASSWORD_CHANGE);
    }

    @Test
    void changeOwnPasswordStoresTheNewHashAndClearsTheRequirement() {
        User user = user(true, new PrivacyConsent("v2", NOW));

        user.changeOwnPassword(new PasswordHash("{argon2}new"));

        assertThat(user.passwordHash()).isEqualTo(new PasswordHash("{argon2}new"));
        assertThat(user.passwordChangeRequired()).isFalse();
        assertThat(user.pendingSteps("v2")).isEmpty();
    }

    @Test
    void acceptPrivacyNoticeRecordsVersionAndInstant() {
        User user = user(false, null);

        user.acceptPrivacyNotice("v2", NOW);

        assertThat(user.privacyConsent()).contains(new PrivacyConsent("v2", NOW));
        assertThat(user.pendingSteps("v2")).isEmpty();
    }

    @Test
    void withdrawingTheConsentLeavesTheNoticePending() {
        User user = user(false, new PrivacyConsent("v2", NOW));

        user.withdrawPrivacyConsent();

        assertThat(user.privacyConsent()).isEmpty();
        assertThat(user.pendingSteps("v2")).containsExactly(PendingStep.PRIVACY_NOTICE);
    }

    @Test
    void acceptingAnOlderVersionLeavesTheNoticePendingForTheCurrentOne() {
        User user = user(false, null);

        user.acceptPrivacyNotice("v1", NOW);

        assertThat(user.pendingSteps("v2")).containsExactly(PendingStep.PRIVACY_NOTICE);
    }

    @Test
    void acceptingTheCurrentVersionReplacesAnOlderConsent() {
        User user = user(false, new PrivacyConsent("v1", NOW.minusSeconds(3600)));

        user.acceptPrivacyNotice("v2", NOW);

        assertThat(user.privacyConsent()).contains(new PrivacyConsent("v2", NOW));
    }

    @Test
    void aNewNoticeVersionAsksAgainEvenWithAnOlderConsent() {
        User user = user(false, new PrivacyConsent("v1", NOW));

        assertThat(user.pendingSteps("v2")).containsExactly(PendingStep.PRIVACY_NOTICE);
        assertThat(user.pendingSteps("v1")).isEmpty();
    }

    @Test
    void noticeComesBeforePasswordWhenBothArePending() {
        User user = user(true, null);

        assertThat(user.pendingSteps("v2")).containsExactly(PendingStep.PRIVACY_NOTICE, PendingStep.PASSWORD_CHANGE);
    }
}
