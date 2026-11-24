package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.application.Fakes.FakeHasher;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FakeSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FakeTokens;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FixedPrivacyNotice;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryCommunities;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemorySessions;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSession;
import io.github.escumartin.neocatecumenal.identity.application.service.AuthenticateSessionService;
import io.github.escumartin.neocatecumenal.identity.application.service.GetMyProfileService;
import io.github.escumartin.neocatecumenal.identity.application.service.LogOutService;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PendingStep;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
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

class AuthenticateSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final String TOKEN = "token-abc";

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final InMemorySessions sessions = new InMemorySessions();
    private final FakeTokens tokens = new FakeTokens();
    private final FakeSessionContext sessionContext = new FakeSessionContext();
    private User user;

    private Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private AuthenticateSessionService authenticatorAt(Instant instant) {
        return new AuthenticateSessionService(sessions, users, tokens, clockAt(instant));
    }

    private Session storeSession(Instant createdAt, Instant lastUsedAt) {
        return sessions.save(new Session(UUID.randomUUID(), user.id(), tokens.hash(TOKEN), createdAt, lastUsedAt));
    }

    @BeforeEach
    void setUp() {
        Community community = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(3)));
        user = users.save(User.restore(new UserId(UUID.randomUUID()), new AccessId(222222), new DisplayName("Test User"),
                UserRole.REGISTERED, community.id(), new FakeHasher().hash(new PlainPassword("irrelevant-pass")), false,
                new PrivacyConsent("v1", NOW.minus(Duration.ofDays(10))), 1L));
    }

    @Test
    void validTokenAuthenticatesTheSessionOwner() {
        Session session = storeSession(NOW, NOW);

        assertThat(authenticatorAt(NOW).authenticate(TOKEN))
                .contains(new CurrentSession(session.id(), user.id()));
    }

    @Test
    void unknownTokenDoesNotAuthenticate() {
        storeSession(NOW, NOW);

        assertThat(authenticatorAt(NOW).authenticate("other-token")).isEmpty();
    }

    @Test
    void blankTokenDoesNotAuthenticate() {
        assertThat(authenticatorAt(NOW).authenticate(" ")).isEmpty();
    }

    @Test
    void sessionIdleForThirtyDaysDoesNotAuthenticate() {
        storeSession(NOW.minus(Duration.ofDays(31)), NOW.minus(Duration.ofDays(30)));

        assertThat(authenticatorAt(NOW).authenticate(TOKEN)).isEmpty();
    }

    @Test
    void sessionOlderThanNinetyDaysDoesNotAuthenticateEvenIfUsedRecently() {
        storeSession(NOW.minus(Duration.ofDays(90)), NOW.minus(Duration.ofMinutes(1)));

        assertThat(authenticatorAt(NOW).authenticate(TOKEN)).isEmpty();
    }

    @Test
    void sessionOfADeletedUserDoesNotAuthenticate() {
        storeSession(NOW, NOW);
        users.users.clear();

        assertThat(authenticatorAt(NOW).authenticate(TOKEN)).isEmpty();
    }

    @Test
    void useAfterFiveMinutesRenewsTheSlidingWindow() {
        Instant created = NOW.minus(Duration.ofDays(5));
        Session session = storeSession(created, NOW.minus(Duration.ofMinutes(5)));

        authenticatorAt(NOW).authenticate(TOKEN);

        assertThat(sessions.findByTokenHash(tokens.hash(TOKEN))).get()
                .extracting(Session::lastUsedAt).isEqualTo(NOW);
        assertThat(sessions.findByTokenHash(tokens.hash(TOKEN))).get()
                .extracting(Session::id).isEqualTo(session.id());
    }

    @Test
    void useBeforeFiveMinutesDoesNotWrite() {
        Instant lastUsed = NOW.minus(Duration.ofMinutes(4));
        storeSession(NOW.minus(Duration.ofDays(5)), lastUsed);

        authenticatorAt(NOW).authenticate(TOKEN);

        assertThat(sessions.findByTokenHash(tokens.hash(TOKEN))).get()
                .extracting(Session::lastUsedAt).isEqualTo(lastUsed);
    }

    @Test
    void logOutDeletesOnlyTheCurrentSession() {
        Session current = storeSession(NOW, NOW);
        sessions.save(new Session(UUID.randomUUID(), user.id(), tokens.hash("other"), NOW, NOW));
        sessionContext.session = new CurrentSession(current.id(), user.id());

        new LogOutService(sessionContext, sessions).logOut();

        assertThat(sessions.findByTokenHash(tokens.hash(TOKEN))).isEmpty();
        assertThat(sessions.findByTokenHash(tokens.hash("other"))).isPresent();
    }

    @Test
    void logOutWithoutSessionDoesNothing() {
        storeSession(NOW, NOW);

        new LogOutService(sessionContext, sessions).logOut();

        assertThat(sessions.sessions).hasSize(1);
    }

    @Test
    void myProfileCombinesUserCommunityAndPendingSteps() {
        sessionContext.session = new CurrentSession(UUID.randomUUID(), user.id());

        var profile = new GetMyProfileService(sessionContext, users, communities, new FixedPrivacyNotice()).get();

        assertThat(profile).isPresent();
        assertThat(profile.get().name()).isEqualTo(new DisplayName("Test User"));
        assertThat(profile.get().communityNumber()).isEqualTo(new CommunityNumber(3));
        assertThat(profile.get().pendingSteps()).containsExactly(PendingStep.PRIVACY_NOTICE);
        assertThat(profile.get().privacyConsent()).contains(new PrivacyConsent("v1", NOW.minus(Duration.ofDays(10))));
    }

    @Test
    void myProfileIsEmptyWithoutSession() {
        assertThat(new GetMyProfileService(sessionContext, users, communities, new FixedPrivacyNotice()).get()).isEmpty();
    }

    @Test
    void myProfileIsEmptyWhenTheUserNoLongerExists() {
        sessionContext.session = new CurrentSession(UUID.randomUUID(), user.id());
        users.users.clear();

        assertThat(new GetMyProfileService(sessionContext, users, communities, new FixedPrivacyNotice()).get()).isEmpty();
    }
}
