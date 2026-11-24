package io.github.escumartin.neocatecumenal.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final String CURRENT_VERSION = "v2";

    private static User registered() {
        return User.register(
                new UserId(UUID.randomUUID()),
                new AccessId(123456),
                new DisplayName("Test User"),
                UserRole.REGISTERED,
                new CommunityId(UUID.randomUUID()),
                new PasswordHash("{argon2}hash"));
    }

    private static User restored(PrivacyConsent consent, boolean passwordChangeRequired) {
        return User.restore(
                new UserId(UUID.randomUUID()),
                new AccessId(123456),
                new DisplayName("Test User"),
                UserRole.REGISTERED,
                new CommunityId(UUID.randomUUID()),
                new PasswordHash("{argon2}hash"),
                passwordChangeRequired,
                consent,
                3L);
    }

    @Test
    void registeredUserIsNew() {
        assertThat(registered().isNew()).isTrue();
    }

    @Test
    void restoredUserIsNotNew() {
        assertThat(restored(null, false).isNew()).isFalse();
    }

    @Test
    void registerRequiresPasswordChange() {
        assertThat(registered().passwordChangeRequired()).isTrue();
    }

    @Test
    void registerLeavesNoPrivacyConsent() {
        assertThat(registered().privacyConsent()).isEmpty();
    }

    @Test
    void registerKeepsGivenData() {
        UserId id = new UserId(UUID.randomUUID());
        CommunityId communityId = new CommunityId(UUID.randomUUID());

        User user = User.register(id, new AccessId(654321), new DisplayName("Ana"), UserRole.MODERATOR,
                communityId, new PasswordHash("{argon2}x"));

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.accessId()).isEqualTo(new AccessId(654321));
        assertThat(user.name()).isEqualTo(new DisplayName("Ana"));
        assertThat(user.role()).isEqualTo(UserRole.MODERATOR);
        assertThat(user.communityId()).isEqualTo(communityId);
        assertThat(user.passwordHash()).isEqualTo(new PasswordHash("{argon2}x"));
    }

    @Test
    void pendingStepsListsNoticeBeforePasswordWhenNoConsent() {
        assertThat(registered().pendingSteps(CURRENT_VERSION))
                .containsExactly(PendingStep.PRIVACY_NOTICE, PendingStep.PASSWORD_CHANGE);
    }

    @Test
    void pendingStepsIsEmptyWhenConsentIsCurrentAndPasswordIsChanged() {
        User user = restored(new PrivacyConsent(CURRENT_VERSION, Instant.parse("2026-01-01T00:00:00Z")), false);

        assertThat(user.pendingSteps(CURRENT_VERSION)).isEmpty();
    }

    @Test
    void pendingStepsRequestsNoticeWhenConsentIsOutdated() {
        User user = restored(new PrivacyConsent("v1", Instant.parse("2026-01-01T00:00:00Z")), false);

        assertThat(user.pendingSteps(CURRENT_VERSION)).isEqualTo(List.of(PendingStep.PRIVACY_NOTICE));
    }

    @Test
    void pendingStepsRequestsPasswordWhenOnlyPasswordChangeIsRequired() {
        User user = restored(new PrivacyConsent(CURRENT_VERSION, Instant.parse("2026-01-01T00:00:00Z")), true);

        assertThat(user.pendingSteps(CURRENT_VERSION)).isEqualTo(List.of(PendingStep.PASSWORD_CHANGE));
    }
}
