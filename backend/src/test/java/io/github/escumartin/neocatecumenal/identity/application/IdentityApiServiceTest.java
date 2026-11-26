package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.CommunityRef;
import io.github.escumartin.neocatecumenal.identity.IdentityRuleViolation;
import io.github.escumartin.neocatecumenal.identity.UserDeleted;
import io.github.escumartin.neocatecumenal.identity.UserRef;
import io.github.escumartin.neocatecumenal.identity.CurrentUser;
import io.github.escumartin.neocatecumenal.identity.Role;
import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FakeHasher;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FakeSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FixedPrivacyNotice;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryCommunities;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemorySessions;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryUsers;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.RecordingEvents;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.RecordingUserLinkGuard;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSession;
import io.github.escumartin.neocatecumenal.identity.application.service.DeleteUserService;
import io.github.escumartin.neocatecumenal.identity.application.service.IdentityApiService;
import io.github.escumartin.neocatecumenal.identity.application.service.UpdateUserService;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentityApiServiceTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryCommunities communities = new InMemoryCommunities(users);
    private final FakeSessionContext sessionContext = new FakeSessionContext();
    private final InMemorySessions sessions = new InMemorySessions();
    private final RecordingEvents events = new RecordingEvents();
    private final Fakes.InMemoryErasures erasures = new Fakes.InMemoryErasures();
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
    private final RecordingUserLinkGuard guard = new RecordingUserLinkGuard(users);
    private IdentityApiService api;
    private Community community;

    @BeforeEach
    void setUp() {
        FixedPrivacyNotice privacyNotice = new FixedPrivacyNotice();
        api = new IdentityApiService(sessionContext, users, communities, privacyNotice,
                new UpdateUserService(sessionContext, users, communities, privacyNotice, guard),
                new DeleteUserService(sessionContext, users, sessions, events, privacyNotice, guard, erasures, clock));
        community = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(7)));
    }

    private User store(UserRole role, PrivacyConsent consent, boolean passwordChangeRequired) {
        return users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(654321),
                new DisplayName("Test Person"), role, community.id(),
                new FakeHasher().hash(new PlainPassword("irrelevant-pass")), passwordChangeRequired, consent));
    }

    private User storeComplete(UserRole role) {
        return store(role, new PrivacyConsent("v2", Instant.parse("2026-03-01T10:00:00Z")), false);
    }

    private void signIn(User user) {
        sessionContext.session = new CurrentSession(UUID.randomUUID(), user.id());
    }

    @Test
    void currentUserIsEmptyWithoutSession() {
        storeComplete(UserRole.MODERATOR);

        assertThat(api.currentUser()).isEmpty();
    }

    @Test
    void currentUserIsEmptyWhenTheSessionOwnerNoLongerExists() {
        User user = storeComplete(UserRole.MODERATOR);
        signIn(user);
        users.users.clear();

        assertThat(api.currentUser()).isEmpty();
    }

    @Test
    void currentUserIsEmptyWhileThePrivacyNoticeIsPending() {
        User user = store(UserRole.MODERATOR, new PrivacyConsent("v1", Instant.parse("2026-03-01T10:00:00Z")), false);
        signIn(user);

        assertThat(api.currentUser()).isEmpty();
    }

    @Test
    void currentUserIsEmptyWhenNeverAcceptedThePrivacyNotice() {
        User user = store(UserRole.MODERATOR, null, false);
        signIn(user);

        assertThat(api.currentUser()).isEmpty();
    }

    @Test
    void currentUserIsEmptyWhileThePasswordChangeIsPending() {
        User user = store(UserRole.MODERATOR, new PrivacyConsent("v2", Instant.parse("2026-03-01T10:00:00Z")), true);
        signIn(user);

        assertThat(api.currentUser()).isEmpty();
    }

    @Test
    void currentUserExposesIdentityRoleAndCommunity() {
        User user = storeComplete(UserRole.MODERATOR);
        signIn(user);

        assertThat(api.currentUser()).contains(new CurrentUser(user.id().value(), 654321, "Test Person",
                Role.MODERATOR, community.id().value()));
    }

    @Test
    void currentUserReadsTheRoleFromTheLatestStoredRow() {
        User user = storeComplete(UserRole.MODERATOR);
        signIn(user);
        users.save(User.restore(user.id(), user.accessId(), user.name(), UserRole.ADMINISTRATOR, user.communityId(),
                user.passwordHash(), false, user.privacyConsent().orElseThrow(), 1L));

        assertThat(api.currentUser()).get().extracting(CurrentUser::role).isEqualTo(Role.ADMINISTRATOR);
    }

    @Test
    void currentUserReadsTheCommunityFromTheLatestStoredRow() {
        User user = storeComplete(UserRole.MODERATOR);
        signIn(user);
        Community other = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(9)));
        users.save(User.restore(user.id(), user.accessId(), user.name(), user.role(), other.id(),
                user.passwordHash(), false, user.privacyConsent().orElseThrow(), 1L));

        assertThat(api.currentUser()).get().extracting(CurrentUser::communityId).isEqualTo(other.id().value());
    }

    @Test
    void communitiesAreOrderedByNumber() {
        Community first = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(2)));
        Community last = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(30)));

        assertThat(api.communities()).containsExactly(
                new CommunityRef(first.id().value(), 2, true),
                new CommunityRef(community.id().value(), 7, true),
                new CommunityRef(last.id().value(), 30, true));
    }

    @Test
    void communitiesAreEmptyWhenThereAreNone() {
        communities.communities.clear();

        assertThat(api.communities()).isEqualTo(List.of());
    }

    @Test
    void communityFindsAnExistingOneById() {
        assertThat(api.community(community.id().value()))
                .contains(new CommunityRef(community.id().value(), 7, true));
    }

    @Test
    void communityIsEmptyForAnUnknownId() {
        assertThat(api.community(UUID.randomUUID())).isEmpty();
    }

    private User storeNamed(String name, UserRole role, Community in) {
        return users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(100000 + users.users.size()),
                new DisplayName(name), role, in.id(), new FakeHasher().hash(new PlainPassword("irrelevant-pass")),
                false, new PrivacyConsent("v2", Instant.parse("2026-03-01T10:00:00Z"))));
    }

    @Test
    void communityRefExposesWhetherTheCommunityIsActive() {
        Community inactive = communities.save(new Community(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(40), false));

        assertThat(api.community(inactive.id().value())).contains(new CommunityRef(inactive.id().value(), 40, false));
        assertThat(api.communities()).contains(new CommunityRef(inactive.id().value(), 40, false));
    }

    @Test
    void userReturnsTheReferenceWithoutAnyActorCheck() {
        User stored = storeComplete(UserRole.REGISTERED);

        assertThat(api.user(stored.id().value())).contains(new UserRef(stored.id().value(), "Test Person",
                Role.REGISTERED, community.id().value()));
    }

    @Test
    void userIsEmptyWhenItDoesNotExist() {
        assertThat(api.user(UUID.randomUUID())).isEmpty();
    }

    @Test
    void lockUserReturnsTheReferenceAfterTakingTheSharedLock() {
        User stored = storeComplete(UserRole.MODERATOR);

        assertThat(api.lockUser(stored.id().value())).contains(new UserRef(stored.id().value(), "Test Person",
                Role.MODERATOR, community.id().value()));
        assertThat(users.calls).contains("lockForShare");
    }

    @Test
    void lockUserIsEmptyWhenItDoesNotExist() {
        assertThat(api.lockUser(UUID.randomUUID())).isEmpty();
    }

    @Test
    void lockCommunityForShareIsTrueAfterTakingTheSharedLock() {
        assertThat(api.lockCommunityForShare(community.id().value())).isTrue();
        assertThat(users.calls).contains("communityLockForShare");
    }

    @Test
    void lockCommunityForShareIsFalseWhenTheCommunityDoesNotExist() {
        assertThat(api.lockCommunityForShare(UUID.randomUUID())).isFalse();
    }

    @Test
    void usersOfListsTheCommunityOrderedByName() {
        User zoe = storeNamed("Zoe Test", UserRole.REGISTERED, community);
        User ana = storeNamed("Ana Test", UserRole.MODERATOR, community);
        Community other = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(8)));
        storeNamed("Other Person", UserRole.REGISTERED, other);

        assertThat(api.usersOf(community.id().value())).extracting(UserRef::id)
                .containsExactly(ana.id().value(), zoe.id().value());
    }

    @Test
    void moveUserLetsAnAdministratorChangeOnlyTheCommunity() {
        User administrator = storeNamed("Admin Person", UserRole.ADMINISTRATOR, community);
        User target = storeNamed("Target Person", UserRole.REGISTERED, community);
        Community destination = communities.save(new Community(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(9)));
        signIn(administrator);

        api.moveUser(target.id().value(), destination.id().value());

        User moved = users.findById(target.id()).orElseThrow();
        assertThat(moved.communityId()).isEqualTo(destination.id());
        assertThat(moved.name()).isEqualTo(new DisplayName("Target Person"));
        assertThat(moved.role()).isEqualTo(UserRole.REGISTERED);
    }

    @Test
    void moveUserIsForbiddenForAModerator() {
        User moderator = storeComplete(UserRole.MODERATOR);
        User target = storeNamed("Target Person", UserRole.REGISTERED, community);
        Community destination = communities.save(new Community(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(9)));
        signIn(moderator);

        assertThatThrownBy(() -> api.moveUser(target.id().value(), destination.id().value()))
                .isInstanceOfSatisfying(IdentityRuleViolation.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void moveUserOnOneselfIsRejected() {
        User administrator = storeNamed("Admin Person", UserRole.ADMINISTRATOR, community);
        Community destination = communities.save(new Community(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(9)));
        signIn(administrator);

        assertThatThrownBy(() -> api.moveUser(administrator.id().value(), destination.id().value()))
                .isInstanceOfSatisfying(IdentityRuleViolation.class,
                        e -> assertThat(e.code()).isEqualTo("SELF_ACTION_FORBIDDEN"));
    }

    @Test
    void moveUserRejectsTakingTheLastModeratorOutOfTheirCommunity() {
        User administrator = storeNamed("Admin Person", UserRole.ADMINISTRATOR, community);
        User moderator = storeNamed("Moderator Person", UserRole.MODERATOR, community);
        Community destination = communities.save(new Community(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(9)));
        signIn(administrator);

        assertThatThrownBy(() -> api.moveUser(moderator.id().value(), destination.id().value()))
                .isInstanceOfSatisfying(IdentityRuleViolation.class,
                        e -> assertThat(e.code()).isEqualTo("LAST_MODERATOR"));
    }

    @Test
    void moveUserRejectsADeactivatedDestination() {
        User administrator = storeNamed("Admin Person", UserRole.ADMINISTRATOR, community);
        User target = storeNamed("Target Person", UserRole.REGISTERED, community);
        Community destination = communities.save(new Community(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(9), false));
        signIn(administrator);

        assertThatThrownBy(() -> api.moveUser(target.id().value(), destination.id().value()))
                .isInstanceOfSatisfying(IdentityRuleViolation.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_DEACTIVATED"));
        assertThat(users.findById(target.id()).orElseThrow().communityId()).isEqualTo(community.id());
    }

    @Test
    void moveUserAsksTheGuardBeforeMovingAnyone() {
        User administrator = storeNamed("Admin Person", UserRole.ADMINISTRATOR, community);
        User target = storeNamed("Target Person", UserRole.REGISTERED, community);
        Community destination = communities.save(new Community(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(9)));
        signIn(administrator);

        api.moveUser(target.id().value(), destination.id().value());

        assertThat(users.calls).contains("guard.allowsCommunityChange");
    }

    @Test
    void deleteUserRemovesTheUserClosesSessionsAndPublishesUserDeleted() {
        User administrator = storeNamed("Admin Person", UserRole.ADMINISTRATOR, community);
        User target = storeNamed("Target Person", UserRole.REGISTERED, community);
        sessions.save(io.github.escumartin.neocatecumenal.identity.domain.Session.open(target.id(),
                "target-hash".getBytes(), Instant.now()));
        signIn(administrator);

        api.deleteUser(target.id().value());

        assertThat(users.findById(target.id())).isEmpty();
        assertThat(sessions.sessions).isEmpty();
        assertThat(events.published).containsExactly(new UserDeleted(target.id().value()));
    }

    @Test
    void deleteUserIsForbiddenForARegisteredActor() {
        User actor = storeComplete(UserRole.REGISTERED);
        User target = storeNamed("Target Person", UserRole.REGISTERED, community);
        signIn(actor);

        assertThatThrownBy(() -> api.deleteUser(target.id().value()))
                .isInstanceOfSatisfying(IdentityRuleViolation.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void deleteUserRejectsDeletingOneself() {
        User administrator = storeNamed("Admin Person", UserRole.ADMINISTRATOR, community);
        signIn(administrator);

        assertThatThrownBy(() -> api.deleteUser(administrator.id().value()))
                .isInstanceOfSatisfying(IdentityRuleViolation.class,
                        e -> assertThat(e.code()).isEqualTo("SELF_ACTION_FORBIDDEN"));
    }

    @Test
    void deleteUserRejectsTheLastModerator() {
        User administrator = storeNamed("Admin Person", UserRole.ADMINISTRATOR, community);
        User moderator = storeNamed("Moderator Person", UserRole.MODERATOR, community);
        signIn(administrator);

        assertThatThrownBy(() -> api.deleteUser(moderator.id().value()))
                .isInstanceOfSatisfying(IdentityRuleViolation.class,
                        e -> assertThat(e.code()).isEqualTo("LAST_MODERATOR"));
    }

    @Test
    void deleteUserRejectsTheLastAdministrator() {
        User first = storeNamed("Admin One", UserRole.ADMINISTRATOR, community);
        Community other = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(9)));
        User second = storeNamed("Admin Two", UserRole.ADMINISTRATOR, other);
        signIn(first);
        users.administratorsSeenAfterLock = 1;

        assertThatThrownBy(() -> api.deleteUser(second.id().value()))
                .isInstanceOfSatisfying(IdentityRuleViolation.class,
                        e -> assertThat(e.code()).isEqualTo("LAST_ADMINISTRATOR"));
    }
}
