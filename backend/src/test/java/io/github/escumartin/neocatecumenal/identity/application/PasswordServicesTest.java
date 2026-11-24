package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.in.FieldValidationException;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UserNotFoundException;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSession;
import io.github.escumartin.neocatecumenal.identity.application.service.AcceptPrivacyNoticeService;
import io.github.escumartin.neocatecumenal.identity.application.service.ChangeOwnPasswordService;
import io.github.escumartin.neocatecumenal.identity.application.service.RejectPrivacyNoticeService;
import io.github.escumartin.neocatecumenal.identity.application.service.ResetUserPasswordService;
import io.github.escumartin.neocatecumenal.identity.application.service.ResolvePendingStepsService;
import io.github.escumartin.neocatecumenal.identity.domain.CommonPasswords;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PendingStep;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PasswordServicesTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final CommonPasswords COMMONS = Set.of("password1")::contains;

    private final UserManagementFixture fixture = new UserManagementFixture();
    private final Fakes.FakeHasher hasher = new Fakes.FakeHasher();
    private final Fakes.InMemoryPasswordChangeAttempts attempts = new Fakes.InMemoryPasswordChangeAttempts();

    private ChangeOwnPasswordService changeService() {
        return new ChangeOwnPasswordService(fixture.currentSession, fixture.users, fixture.sessions, hasher, COMMONS,
                fixture.privacyNotice, attempts);
    }

    private ResetUserPasswordService resetService() {
        return new ResetUserPasswordService(fixture.currentSession, fixture.users, fixture.sessions, hasher,
                new Fakes.FixedInitialPassword(), fixture.privacyNotice);
    }

    private AcceptPrivacyNoticeService acceptService() {
        return new AcceptPrivacyNoticeService(fixture.currentSession, fixture.users, fixture.privacyNotice, CLOCK);
    }

    private RejectPrivacyNoticeService rejectService() {
        return new RejectPrivacyNoticeService(fixture.currentSession, fixture.users, fixture.sessions);
    }

    private ResolvePendingStepsService pendingService() {
        return new ResolvePendingStepsService(fixture.currentSession, fixture.users, fixture.privacyNotice);
    }

    private static void assertDomainCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    private static void assertFieldError(Runnable action, String field, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(FieldValidationException.class, e -> {
            assertThat(e.field()).isEqualTo(field);
            assertThat(e.code()).isEqualTo(code);
        });
    }

    private User withPending(UserRole role, boolean passwordChangeRequired, PrivacyConsent consent) {
        User base = fixture.add(role, fixture.communityA);
        return fixture.users.save(TestUsers.newUser(base.id(), base.accessId(), base.name(), role, base.communityId(),
                base.passwordHash(), passwordChangeRequired, consent));
    }

    private Session sessionOf(User user, String tag) {
        return fixture.sessions.save(Session.open(user.id(), ("hash-" + tag).getBytes(), NOW));
    }

    private Session actAsWithSession(User user) {
        Session current = sessionOf(user, "current-" + user.id().value());
        fixture.currentSession.session = new CurrentSession(current.id(), user.id());
        return current;
    }

    private User stored(User user) {
        return fixture.users.findById(user.id()).orElseThrow();
    }

    @Test
    void changeStoresTheNewPasswordAndClearsTheInitialRequirement() {
        User user = withPending(UserRole.REGISTERED, true, new PrivacyConsent("v2", NOW));
        actAsWithSession(user);

        changeService().change(new PlainPassword("secret"), new PlainPassword("n3w-passphrase"));

        assertThat(stored(user).passwordHash()).isEqualTo(new PasswordHash("{fake}n3w-passphrase"));
        assertThat(stored(user).passwordChangeRequired()).isFalse();
    }

    @Test
    void changeKeepsTheCurrentSessionAndClosesTheOthersOfTheSameUser() {
        User user = withPending(UserRole.REGISTERED, false, new PrivacyConsent("v2", NOW));
        User someoneElse = withPending(UserRole.REGISTERED, false, new PrivacyConsent("v2", NOW));
        Session current = actAsWithSession(user);
        Session other = sessionOf(user, "other");
        Session unrelated = sessionOf(someoneElse, "unrelated");

        changeService().change(new PlainPassword("secret"), new PlainPassword("n3w-passphrase"));

        assertThat(fixture.sessions.sessions.values()).extracting(Session::id)
                .contains(current.id(), unrelated.id())
                .doesNotContain(other.id());
    }

    @Test
    void changeWithAWrongCurrentPasswordIsRejectedAndChangesNothing() {
        User user = withPending(UserRole.REGISTERED, false, new PrivacyConsent("v2", NOW));
        actAsWithSession(user);
        Session other = sessionOf(user, "other");

        assertFieldError(() -> changeService().change(new PlainPassword("wrong"),
                new PlainPassword("n3w-passphrase")), "currentPassword", "CURRENT_PASSWORD_INCORRECT");

        assertThat(stored(user).passwordHash()).isEqualTo(new PasswordHash("{fake}secret"));
        assertThat(fixture.sessions.sessions.values()).extracting(Session::id).contains(other.id());
    }

    @Test
    void fourWrongCurrentPasswordsInARowKeepEverySessionOpen() {
        User user = withPending(UserRole.REGISTERED, false, new PrivacyConsent("v2", NOW));
        actAsWithSession(user);
        Session other = sessionOf(user, "other");

        for (int attempt = 0; attempt < 4; attempt++) {
            assertFieldError(() -> changeService().change(new PlainPassword("wrong"),
                    new PlainPassword("n3w-passphrase")), "currentPassword", "CURRENT_PASSWORD_INCORRECT");
        }

        assertThat(fixture.sessions.sessions.values()).extracting(Session::id).contains(other.id());
    }

    @Test
    void theFifthWrongCurrentPasswordInARowClosesEverySessionAndIsUnauthenticated() {
        User user = withPending(UserRole.REGISTERED, false, new PrivacyConsent("v2", NOW));
        Session current = actAsWithSession(user);
        Session other = sessionOf(user, "other");
        Session unrelated = sessionOf(fixture.add(UserRole.REGISTERED, fixture.communityA), "unrelated");
        for (int attempt = 0; attempt < 4; attempt++) {
            assertFieldError(() -> changeService().change(new PlainPassword("wrong"),
                    new PlainPassword("n3w-passphrase")), "currentPassword", "CURRENT_PASSWORD_INCORRECT");
        }

        assertDomainCode(() -> changeService().change(new PlainPassword("wrong"),
                new PlainPassword("n3w-passphrase")), "UNAUTHENTICATED");

        assertThat(fixture.sessions.sessions.values()).extracting(Session::id)
                .contains(unrelated.id())
                .doesNotContain(current.id(), other.id());
        assertThat(stored(user).passwordHash()).isEqualTo(new PasswordHash("{fake}secret"));
    }

    @Test
    void aCorrectCurrentPasswordResetsTheFailureCounter() {
        User user = withPending(UserRole.REGISTERED, false, new PrivacyConsent("v2", NOW));
        Session current = actAsWithSession(user);
        for (int attempt = 0; attempt < 4; attempt++) {
            assertFieldError(() -> changeService().change(new PlainPassword("wrong"),
                    new PlainPassword("n3w-passphrase")), "currentPassword", "CURRENT_PASSWORD_INCORRECT");
        }
        changeService().change(new PlainPassword("secret"), new PlainPassword("n3w-passphrase"));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertFieldError(() -> changeService().change(new PlainPassword("wrong"),
                    new PlainPassword("another-passphrase")), "currentPassword", "CURRENT_PASSWORD_INCORRECT");
        }

        assertThat(fixture.sessions.sessions.values()).extracting(Session::id).contains(current.id());
    }

    @Test
    void changeAppliesThePasswordPolicyToTheNewPassword() {
        User user = withPending(UserRole.REGISTERED, false, new PrivacyConsent("v2", NOW));
        actAsWithSession(user);
        PlainPassword current = new PlainPassword("secret");

        assertFieldError(() -> changeService().change(current, new PlainPassword("short")), "newPassword",
                "PASSWORD_TOO_SHORT");
        assertFieldError(() -> changeService().change(current, new PlainPassword("a".repeat(65))), "newPassword",
                "PASSWORD_TOO_LONG");
        assertFieldError(() -> changeService().change(current, new PlainPassword("Password1")), "newPassword",
                "PASSWORD_TOO_COMMON");
        assertFieldError(() -> changeService().change(current,
                new PlainPassword("x" + user.accessId().value() + "x")), "newPassword",
                "PASSWORD_CONTAINS_ACCESS_ID");
        assertThat(stored(user).passwordHash()).isEqualTo(new PasswordHash("{fake}secret"));
    }

    @Test
    void changeIsForbiddenWhileThePrivacyNoticeIsPending() {
        User user = withPending(UserRole.REGISTERED, true, null);
        actAsWithSession(user);

        assertDomainCode(() -> changeService().change(new PlainPassword("secret"),
                new PlainPassword("n3w-passphrase")), "FORBIDDEN");
        assertThat(stored(user).passwordChangeRequired()).isTrue();
    }

    @Test
    void changeWithoutASessionIsUnauthenticated() {
        assertDomainCode(() -> changeService().change(new PlainPassword("secret"),
                new PlainPassword("n3w-passphrase")), "UNAUTHENTICATED");
    }

    @Test
    void administratorResetsAnyUserAndGetsTheInitialPasswordOnce() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = withPending(UserRole.MODERATOR, false, new PrivacyConsent("v2", NOW));

        PlainPassword initial = resetService().reset(target.id());

        assertThat(initial).isEqualTo(new PlainPassword("initialpass"));
        assertThat(stored(target).passwordHash()).isEqualTo(new PasswordHash("{fake}initialpass"));
        assertThat(stored(target).passwordChangeRequired()).isTrue();
    }

    @Test
    void resetClosesEverySessionOfTheTargetAndNoOtherSession() {
        User administrator = fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);
        Session first = sessionOf(target, "first");
        Session second = sessionOf(target, "second");
        Session actorSession = sessionOf(administrator, "actor");

        resetService().reset(target.id());

        assertThat(fixture.sessions.sessions.values()).extracting(Session::id)
                .contains(actorSession.id())
                .doesNotContain(first.id(), second.id());
    }

    @Test
    void moderatorResetsARegisteredUserOfTheirCommunity() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        resetService().reset(target.id());

        assertThat(stored(target).passwordChangeRequired()).isTrue();
    }

    @Test
    void moderatorCannotResetAUserOfAnotherCommunityOrOfAHigherRole() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));
        User otherCommunity = fixture.add(UserRole.REGISTERED, fixture.communityB);
        User fellowModerator = fixture.add(UserRole.MODERATOR, fixture.communityA);
        User administrator = fixture.add(UserRole.ADMINISTRATOR, fixture.communityA);

        assertDomainCode(() -> resetService().reset(otherCommunity.id()), "FORBIDDEN");
        assertDomainCode(() -> resetService().reset(fellowModerator.id()), "FORBIDDEN");
        assertDomainCode(() -> resetService().reset(administrator.id()), "FORBIDDEN");
        assertThat(stored(otherCommunity).passwordHash()).isEqualTo(new PasswordHash("{fake}secret"));
    }

    @Test
    void registeredUserCannotResetAnyPassword() {
        fixture.actAs(fixture.add(UserRole.REGISTERED, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        assertDomainCode(() -> resetService().reset(target.id()), "FORBIDDEN");
    }

    @Test
    void nobodyResetsTheirOwnPassword() {
        User administrator = fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));
        User moderator = fixture.add(UserRole.MODERATOR, fixture.communityA);

        assertDomainCode(() -> resetService().reset(administrator.id()), "SELF_ACTION_FORBIDDEN");
        fixture.actAs(moderator);
        assertDomainCode(() -> resetService().reset(moderator.id()), "SELF_ACTION_FORBIDDEN");
        assertThat(stored(moderator).passwordChangeRequired()).isFalse();
    }

    @Test
    void resettingAMissingUserIsNotFound() {
        fixture.actAs(fixture.add(UserRole.ADMINISTRATOR, fixture.communityA));

        assertThatThrownBy(() -> resetService().reset(new UserId(UUID.randomUUID())))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void resetByAnActorWithPendingStepsIsForbidden() {
        fixture.actAs(withPending(UserRole.ADMINISTRATOR, true, new PrivacyConsent("v2", NOW)));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        assertDomainCode(() -> resetService().reset(target.id()), "FORBIDDEN");
    }

    @Test
    void acceptingTheCurrentVersionRecordsTheConsentWithTheClockInstant() {
        User user = withPending(UserRole.REGISTERED, true, null);
        fixture.actAs(user);

        acceptService().accept("v2");

        assertThat(stored(user).privacyConsent()).contains(new PrivacyConsent("v2", NOW));
        assertThat(stored(user).pendingSteps("v2")).containsExactly(PendingStep.PASSWORD_CHANGE);
    }

    @Test
    void acceptingAnotherVersionIsRejectedAndRecordsNothing() {
        User user = withPending(UserRole.REGISTERED, false, null);
        fixture.actAs(user);

        assertDomainCode(() -> acceptService().accept("v1"), "PRIVACY_NOTICE_OUTDATED");

        assertThat(stored(user).privacyConsent()).isEmpty();
    }

    @Test
    void acceptingWithoutASessionIsUnauthenticated() {
        assertDomainCode(() -> acceptService().accept("v2"), "UNAUTHENTICATED");
    }

    @Test
    void rejectingClosesOnlyTheCurrentSessionAndTheNoticeStaysPending() {
        User user = withPending(UserRole.REGISTERED, false, null);
        Session current = actAsWithSession(user);
        Session other = sessionOf(user, "other");

        rejectService().reject();

        assertThat(fixture.sessions.sessions.values()).extracting(Session::id)
                .contains(other.id())
                .doesNotContain(current.id());
        assertThat(stored(user).pendingSteps("v2")).containsExactly(PendingStep.PRIVACY_NOTICE);
    }

    @Test
    void rejectingWithdrawsAnAlreadyRecordedAcceptance() {
        User user = withPending(UserRole.REGISTERED, false, new PrivacyConsent("v2", NOW));
        actAsWithSession(user);

        rejectService().reject();

        assertThat(stored(user).privacyConsent()).isEmpty();
        assertThat(stored(user).pendingSteps("v2")).containsExactly(PendingStep.PRIVACY_NOTICE);
    }

    @Test
    void rejectingWithoutASessionIsUnauthenticated() {
        assertDomainCode(() -> rejectService().reject(), "UNAUTHENTICATED");
    }

    @Test
    void pendingStepsOfTheCurrentSessionFollowTheFixedOrder() {
        fixture.actAs(withPending(UserRole.REGISTERED, true, null));

        assertThat(pendingService().ofCurrentSession())
                .containsExactly(PendingStep.PRIVACY_NOTICE, PendingStep.PASSWORD_CHANGE);
    }

    @Test
    void noPendingStepsForAFullyOnboardedUserOrWithoutSession() {
        assertThat(pendingService().ofCurrentSession()).isEmpty();
        fixture.actAs(fixture.add(UserRole.REGISTERED, fixture.communityA));

        assertThat(pendingService().ofCurrentSession()).isEmpty();
    }
}
