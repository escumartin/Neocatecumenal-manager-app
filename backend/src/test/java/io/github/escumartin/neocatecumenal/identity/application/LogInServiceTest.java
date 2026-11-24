package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.application.Fakes.FakeHasher;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FakeTokens;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FixedPrivacyNotice;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryCommunities;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemorySessions;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryThrottleStore;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.in.LoginCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.LoginResult;
import io.github.escumartin.neocatecumenal.identity.application.service.LogInService;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PendingStep;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogInServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final AccessId ACCESS_ID = new AccessId(123456);
    private static final String IP = "10.0.0.7";
    private static final String PASSWORD = "correct-horse";

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final InMemorySessions sessions = new InMemorySessions();
    private final FakeHasher hasher = new FakeHasher();
    private final InMemoryThrottleStore throttle = new InMemoryThrottleStore();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private LogInService service;
    private User user;

    @BeforeEach
    void setUp() {
        Community community = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(7)));
        user = users.save(User.restore(new UserId(UUID.randomUUID()), ACCESS_ID, new DisplayName("Test User"),
                UserRole.MODERATOR, community.id(), hasher.hash(new PlainPassword(PASSWORD)), false,
                new PrivacyConsent("v2", NOW.minusSeconds(3600)), 1L));
        service = new LogInService(users, communities, sessions, hasher, new FakeTokens(), throttle,
                new FixedPrivacyNotice(), clock);
    }

    private LoginResult attempt(String password) {
        return attemptFrom(ACCESS_ID, password, IP);
    }

    private LoginResult attemptFrom(AccessId accessId, String password, String ip) {
        return service.logIn(new LoginCommand(accessId, new PlainPassword(password), ip));
    }

    @Test
    void correctCredentialsReturnTokenAndProfile() {
        LoginResult result = attempt(PASSWORD);

        assertThat(result).isInstanceOf(LoginResult.LoggedIn.class);
        LoginResult.LoggedIn loggedIn = (LoginResult.LoggedIn) result;
        assertThat(loggedIn.token()).isEqualTo("token-1");
        assertThat(loggedIn.profile().userId()).isEqualTo(user.id());
        assertThat(loggedIn.profile().communityNumber()).isEqualTo(new CommunityNumber(7));
        assertThat(loggedIn.profile().role()).isEqualTo(UserRole.MODERATOR);
        assertThat(loggedIn.profile().pendingSteps()).isEmpty();
    }

    @Test
    void profileReportsPendingStepsOfTheUser() {
        users.save(User.register(user.id(), ACCESS_ID, user.name(), UserRole.REGISTERED, user.communityId(),
                hasher.hash(new PlainPassword(PASSWORD))));

        LoginResult.LoggedIn loggedIn = (LoginResult.LoggedIn) attempt(PASSWORD);

        assertThat(loggedIn.profile().pendingSteps())
                .containsExactly(PendingStep.PRIVACY_NOTICE, PendingStep.PASSWORD_CHANGE);
        assertThat(loggedIn.profile().currentPrivacyNoticeVersion()).isEqualTo("v2");
    }

    @Test
    void correctCredentialsStoreOnlyTheHashOfTheToken() {
        attempt(PASSWORD);

        assertThat(sessions.sessions).hasSize(1);
        var session = sessions.sessions.values().iterator().next();
        assertThat(session.userId()).isEqualTo(user.id());
        assertThat(session.tokenHash()).isEqualTo(new FakeTokens().hash("token-1"));
        assertThat(session.createdAt()).isEqualTo(NOW);
        assertThat(session.lastUsedAt()).isEqualTo(NOW);
    }

    @Test
    void wrongPasswordIsInvalidCredentialsAndCreatesNoSession() {
        LoginResult result = attempt("wrong-password");

        assertThat(result).isEqualTo(new LoginResult.InvalidCredentials());
        assertThat(sessions.sessions).isEmpty();
    }

    @Test
    void unknownAccessIdGivesTheSameResultAsAWrongPassword() {
        LoginResult unknown = attemptFrom(new AccessId(654321), PASSWORD, IP);
        LoginResult wrong = attempt("wrong-password");

        assertThat(unknown).isEqualTo(wrong);
    }

    @Test
    void unknownAccessIdStillSpendsAHashVerification() {
        attemptFrom(new AccessId(654321), PASSWORD, IP);

        assertThat(hasher.dummyVerifications).isEqualTo(1);
        assertThat(hasher.verifications).isZero();
    }

    @Test
    void attemptIsCountedAsFailureBeforeThePasswordIsVerified() {
        int[] failuresWhileVerifying = new int[1];
        hasher.beforeVerification = () -> failuresWhileVerifying[0] = throttle.pair(ACCESS_ID, IP).consecutiveFailures();

        attempt(PASSWORD);

        assertThat(failuresWhileVerifying[0]).isEqualTo(1);
    }

    @Test
    void locksTheIpRowBeforeThePairRow() {
        attempt(PASSWORD);

        assertThat(throttle.lockOrder).containsExactly("ip", "pair");
    }

    @Test
    void successfulLoginRemovesThePairRowAndRestoresTheIpState() {
        attempt("wrong-password");
        var ipAfterFailure = throttle.ips.get(IP);

        attempt(PASSWORD);

        assertThat(throttle.pairs).isEmpty();
        assertThat(ipAfterFailure.recentFailures()).hasSize(1);
        assertThat(throttle.ips.get(IP).recentFailures()).hasSize(1);
    }

    @Test
    void successfulLoginDoesNotAddToTheIpWindow() {
        for (int i = 0; i < 25; i++) {
            assertThat(attempt(PASSWORD)).isInstanceOf(LoginResult.LoggedIn.class);
        }

        assertThat(throttle.ips.get(IP).recentFailures()).isEmpty();
        assertThat(throttle.ips.get(IP).blockedUntil()).isNull();
    }

    @Test
    void sixthConsecutiveAttemptIsBlockedWithRetryAfterAndWithoutVerifying() {
        for (int i = 0; i < 5; i++) {
            attempt("wrong-password");
        }
        int verificationsBefore = hasher.verifications;

        LoginResult result = attempt(PASSWORD);

        assertThat(result).isEqualTo(new LoginResult.Blocked(Duration.ofMinutes(15)));
        assertThat(hasher.verifications).isEqualTo(verificationsBefore);
        assertThat(sessions.sessions).isEmpty();
    }

    @Test
    void failuresOfANonexistentAccessIdBlockItToo() {
        AccessId ghost = new AccessId(999001);
        for (int i = 0; i < 5; i++) {
            attemptFrom(ghost, "whatever-password", IP);
        }

        assertThat(attemptFrom(ghost, "whatever-password", IP)).isInstanceOf(LoginResult.Blocked.class);
    }

    @Test
    void failuresFromAnotherIpDoNotBlockTheHolder() {
        for (int i = 0; i < 6; i++) {
            attemptFrom(ACCESS_ID, "wrong-password", "10.0.0.99");
        }

        assertThat(attempt(PASSWORD)).isInstanceOf(LoginResult.LoggedIn.class);
    }

    @Test
    void twentyFailuresFromOneIpBlockEveryAccessIdFromIt() {
        for (int i = 0; i < 20; i++) {
            attemptFrom(new AccessId(300000 + i), "wrong-password", IP);
        }

        assertThat(attempt(PASSWORD)).isInstanceOf(LoginResult.Blocked.class);
        assertThat(throttle.ips.get(IP).blockedUntil()).isEqualTo(NOW.plus(LoginThrottle.BLOCK));
    }
}
