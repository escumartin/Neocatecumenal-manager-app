package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.CommunityRef;
import io.github.escumartin.neocatecumenal.identity.CurrentUser;
import io.github.escumartin.neocatecumenal.identity.Role;
import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FakeHasher;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FakeSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FixedPrivacyNotice;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryCommunities;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSession;
import io.github.escumartin.neocatecumenal.identity.application.service.IdentityApiService;
import io.github.escumartin.neocatecumenal.identity.application.service.ListCommunitiesService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentityApiServiceTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final FakeSessionContext sessionContext = new FakeSessionContext();
    private IdentityApiService api;
    private Community community;

    @BeforeEach
    void setUp() {
        api = new IdentityApiService(sessionContext, users, communities, new FixedPrivacyNotice(),
                new ListCommunitiesService(communities));
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
                new CommunityRef(first.id().value(), 2),
                new CommunityRef(community.id().value(), 7),
                new CommunityRef(last.id().value(), 30));
    }

    @Test
    void communitiesAreEmptyWhenThereAreNone() {
        communities.communities.clear();

        assertThat(api.communities()).isEqualTo(List.of());
    }

    @Test
    void communityFindsAnExistingOneById() {
        assertThat(api.community(community.id().value()))
                .contains(new CommunityRef(community.id().value(), 7));
    }

    @Test
    void communityIsEmptyForAnUnknownId() {
        assertThat(api.community(UUID.randomUUID())).isEmpty();
    }

    @Test
    void listCommunitiesServiceReturnsThemOrderedByNumber() {
        Community first = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(1)));

        assertThat(new ListCommunitiesService(communities).list())
                .containsExactly(new CommunityRef(first.id().value(), 1), new CommunityRef(community.id().value(), 7));
    }
}
