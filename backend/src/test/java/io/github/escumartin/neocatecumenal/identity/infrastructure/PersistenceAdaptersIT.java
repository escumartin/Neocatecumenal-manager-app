package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.out.AccessIdAlreadyTakenException;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Actor;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.MembershipCounts;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserChanges;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence.CommunityPersistenceAdapter;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence.PasswordChangeAttemptsPersistenceAdapter;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence.SessionPersistenceAdapter;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence.UserPersistenceAdapter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({UserPersistenceAdapter.class, CommunityPersistenceAdapter.class, SessionPersistenceAdapter.class,
        PasswordChangeAttemptsPersistenceAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PersistenceAdaptersIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private UserPersistenceAdapter users;

    @Autowired
    private CommunityPersistenceAdapter communities;

    @Autowired
    private SessionPersistenceAdapter sessions;

    @Autowired
    private PasswordChangeAttemptsPersistenceAdapter attempts;

    private Community newCommunity(int number) {
        return communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(number)));
    }

    private User newUser(Community community, int accessId) {
        return User.register(new UserId(UUID.randomUUID()), new AccessId(accessId), new DisplayName("Test User"),
                UserRole.MODERATOR, community.id(), new PasswordHash("{argon2}x"));
    }

    private static void rename(User user, String name) {
        Actor administrator = new Actor(new UserId(UUID.randomUUID()), UserRole.ADMINISTRATOR, user.communityId());
        user.update(administrator, new UserChanges(new DisplayName(name), user.role(), user.communityId()),
                new MembershipCounts() {
                    @Override
                    public int administrators() {
                        return 2;
                    }

                    @Override
                    public long moderatorsIn(CommunityId communityId) {
                        return 2;
                    }
                });
    }

    @Test
    void savedCommunitiesAreListedByNumber() {
        Community third = newCommunity(903);
        Community first = newCommunity(901);
        Community second = newCommunity(902);

        List<Community> listed = communities.findAll().stream()
                .filter(community -> community.number().value() >= 901 && community.number().value() <= 903).toList();

        assertThat(listed).containsExactly(first, second, third);
        assertThat(communities.findById(second.id())).contains(second);
    }

    @Test
    void unknownCommunityIsEmpty() {
        assertThat(communities.findById(new CommunityId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void savedUserIsFoundByIdAndByAccessId() {
        Community community = newCommunity(911);
        User user = users.save(newUser(community, 211111));

        assertThat(users.findById(user.id())).get().usingRecursiveComparison().isEqualTo(user);
        assertThat(users.findByAccessId(new AccessId(211111))).get().extracting(User::id).isEqualTo(user.id());
    }

    @Test
    void unknownUserIsEmpty() {
        assertThat(users.findById(new UserId(UUID.randomUUID()))).isEmpty();
        assertThat(users.findByAccessId(new AccessId(299999))).isEmpty();
    }

    @Test
    void userPrivacyConsentIsPersisted() {
        Community community = newCommunity(912);
        User user = users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(212222),
                new DisplayName("Test User"), UserRole.REGISTERED, community.id(), new PasswordHash("{argon2}x"),
                false, new PrivacyConsent("v1", Instant.parse("2026-02-01T10:00:00Z"))));

        assertThat(users.findById(user.id())).get().extracting(User::privacyConsent)
                .isEqualTo(Optional.of(new PrivacyConsent("v1", Instant.parse("2026-02-01T10:00:00Z"))));
    }

    @Test
    void passwordChangeFailuresAreCountedPerUserAndClearedOnDemand() {
        Community community = newCommunity(931);
        User first = users.save(newUser(community, 291111));
        User second = users.save(newUser(community, 291112));

        assertThat(attempts.recordFailure(first.id())).isEqualTo(1);
        assertThat(attempts.recordFailure(first.id())).isEqualTo(2);
        assertThat(attempts.recordFailure(second.id())).isEqualTo(1);
        attempts.clear(first.id());

        assertThat(attempts.recordFailure(first.id())).isEqualTo(1);
        assertThat(attempts.recordFailure(second.id())).isEqualTo(2);
    }

    @Test
    void countingPasswordChangeFailuresDoesNotInvalidateTheLoadedUser() {
        Community community = newCommunity(932);
        User saved = users.save(newUser(community, 292222));
        User loaded = users.findById(saved.id()).orElseThrow();
        attempts.recordFailure(saved.id());
        rename(loaded, "Renamed Again");

        assertThat(users.save(loaded).name()).isEqualTo(new DisplayName("Renamed Again"));
    }

    @Test
    void duplicatedAccessIdIsRejected() {
        Community community = newCommunity(913);
        users.save(newUser(community, 213333));

        assertThatThrownBy(() -> users.save(newUser(community, 213333)))
                .isInstanceOf(AccessIdAlreadyTakenException.class);
    }

    @Test
    void staleUserVersionIsRejected() {
        Community community = newCommunity(914);
        User saved = users.save(newUser(community, 214444));
        User first = users.findById(saved.id()).orElseThrow();
        User second = users.findById(saved.id()).orElseThrow();
        rename(first, "First Edit");
        users.save(first);
        rename(second, "Second Edit");

        assertThatThrownBy(() -> users.save(second)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void renamedUserKeepsNewNameAndIncrementsVersion() {
        Community community = newCommunity(915);
        User saved = users.save(newUser(community, 215555));
        User loaded = users.findById(saved.id()).orElseThrow();
        rename(loaded, "Renamed");

        User updated = users.save(loaded);

        assertThat(updated.name()).isEqualTo(new DisplayName("Renamed"));
        assertThat(updated.version()).isGreaterThan(saved.version());
    }

    @Test
    void savedSessionIsFoundByTokenHash() {
        Community community = newCommunity(916);
        User user = users.save(newUser(community, 216666));
        Session session = sessions.save(new Session(UUID.randomUUID(), user.id(), new byte[] {9, 8, 7},
                Instant.parse("2026-03-01T10:00:00Z"), Instant.parse("2026-03-01T10:00:00Z")));

        assertThat(sessions.findByTokenHash(new byte[] {9, 8, 7})).get().extracting(Session::id).isEqualTo(session.id());
        assertThat(sessions.findByTokenHash(new byte[] {1, 1, 1})).isEmpty();
    }

    @Test
    void deletingAllSessionsOfAUserLeavesOthers() {
        Community community = newCommunity(917);
        User owner = users.save(newUser(community, 217777));
        User other = users.save(newUser(community, 217778));
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        sessions.save(new Session(UUID.randomUUID(), owner.id(), new byte[] {5, 0}, now, now));
        sessions.save(new Session(UUID.randomUUID(), other.id(), new byte[] {5, 1}, now, now));

        sessions.deleteAllOf(owner.id());

        assertThat(sessions.findByTokenHash(new byte[] {5, 0})).isEmpty();
        assertThat(sessions.findByTokenHash(new byte[] {5, 1})).isPresent();
    }
    @Test
    void communityIsFoundByItsNumber() {
        Community community = newCommunity(921);

        assertThat(communities.findByNumber(new CommunityNumber(921))).contains(community);
        assertThat(communities.findByNumber(new CommunityNumber(928))).isEmpty();
    }

    @Test
    void administratorPresenceIsDetectedOnlyForTheAdministratorRole() {
        Community community = newCommunity(922);
        users.save(newUser(community, 222221));

        assertThat(users.existsAdministrator()).isFalse();

        users.save(User.register(new UserId(UUID.randomUUID()), new AccessId(222222), new DisplayName("Admin User"),
                UserRole.ADMINISTRATOR, community.id(), new PasswordHash("{argon2}x")));

        assertThat(users.existsAdministrator()).isTrue();
    }

    @Test
    void deletingASessionByIdLeavesTheOtherSessionsOfTheSameUser() {
        Community community = newCommunity(923);
        User owner = users.save(newUser(community, 223333));
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        Session closed = sessions.save(new Session(UUID.randomUUID(), owner.id(), new byte[] {6, 0}, now, now));
        sessions.save(new Session(UUID.randomUUID(), owner.id(), new byte[] {6, 1}, now, now));

        sessions.delete(closed.id());

        assertThat(sessions.findByTokenHash(new byte[] {6, 0})).isEmpty();
        assertThat(sessions.findByTokenHash(new byte[] {6, 1})).isPresent();
    }

    @Test
    void retainingMostRecentlyUsedDeletesTheOldestSurplusSessionsOfThatUserOnly() {
        Community community = newCommunity(925);
        User owner = users.save(newUser(community, 225555));
        User other = users.save(newUser(community, 225556));
        Instant base = Instant.parse("2026-03-01T10:00:00Z");
        for (int i = 0; i < 4; i++) {
            Instant lastUse = base.plusSeconds(i * 100L);
            sessions.save(new Session(UUID.randomUUID(), owner.id(), new byte[] {8, (byte) i}, base, lastUse));
        }
        sessions.save(new Session(UUID.randomUUID(), other.id(), new byte[] {8, 9}, base, base.minusSeconds(900)));

        sessions.retainMostRecentlyUsed(owner.id(), 2);

        assertThat(sessions.findByTokenHash(new byte[] {8, 0})).isEmpty();
        assertThat(sessions.findByTokenHash(new byte[] {8, 1})).isEmpty();
        assertThat(sessions.findByTokenHash(new byte[] {8, 2})).isPresent();
        assertThat(sessions.findByTokenHash(new byte[] {8, 3})).isPresent();
        assertThat(sessions.findByTokenHash(new byte[] {8, 9})).isPresent();
    }

    @Test
    void retainingMostRecentlyUsedKeepsEverythingBelowTheLimit() {
        Community community = newCommunity(926);
        User owner = users.save(newUser(community, 226666));
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        sessions.save(new Session(UUID.randomUUID(), owner.id(), new byte[] {4, 0}, now, now));

        sessions.retainMostRecentlyUsed(owner.id(), 5);

        assertThat(sessions.findByTokenHash(new byte[] {4, 0})).isPresent();
    }

    @Test
    void touchedSessionKeepsItsIdentityAndStoresTheNewLastUse() {
        Community community = newCommunity(924);
        User owner = users.save(newUser(community, 224444));
        Instant now = Instant.parse("2026-03-01T10:00:00Z");
        Session session = sessions.save(new Session(UUID.randomUUID(), owner.id(), new byte[] {7, 0}, now, now));

        sessions.save(session.touch(now.plusSeconds(600)));

        assertThat(sessions.findByTokenHash(new byte[] {7, 0})).get()
                .extracting(Session::lastUsedAt).isEqualTo(now.plusSeconds(600));
    }
}
