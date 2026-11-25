package io.github.escumartin.neocatecumenal.identity.infrastructure.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.IdentityRuleViolation;
import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class CommunityUserLockingIT {

    private static final String LOCK_NOT_AVAILABLE = "55P03";
    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(500000);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private UserRepository users;

    @Autowired
    private CommunityRepository communities;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Community community;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
        community = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(1)));
    }

    private User storeUser(String name, UserRole role, Community in) {
        return users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(ACCESS_IDS.incrementAndGet()),
                new DisplayName(name), role, in.id(), new PasswordHash("{argon2}hash"), false,
                new PrivacyConsent("v1", Instant.now())));
    }

    private static void tryLock(UUID userId, String mode) throws SQLException {
        try (Connection other = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); Statement statement = other.createStatement()) {
            statement.execute("select 1 from identity_user where id = '" + userId + "' for " + mode + " nowait");
        }
    }

    private static void assertLockNotAvailable(UUID userId, String mode) {
        assertThatThrownBy(() -> tryLock(userId, mode)).isInstanceOfSatisfying(SQLException.class,
                e -> assertThat(e.getSQLState()).isEqualTo(LOCK_NOT_AVAILABLE));
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    @Test
    void findByIdForUpdateTakesAnExclusiveRowLock() {
        User user = storeUser("Test Person", UserRole.REGISTERED, community);

        inTransaction(() -> {
            assertThat(users.findByIdForUpdate(user.id())).isPresent();
            assertLockNotAvailable(user.id().value(), "update");
            assertLockNotAvailable(user.id().value(), "share");
            return null;
        });
    }

    @Test
    void findByIdForUpdateIsEmptyForAnUnknownUser() {
        assertThat(inTransaction(() -> users.findByIdForUpdate(new UserId(UUID.randomUUID())))).isEmpty();
    }

    @Test
    void findByIdForUpdateReturnsTheLatestCommittedState() {
        User user = storeUser("Test Person", UserRole.REGISTERED, community);

        inTransaction(() -> {
            assertThat(users.findById(user.id()).orElseThrow().role()).isEqualTo(UserRole.REGISTERED);
            jdbc.update("update identity_user set role = 'MODERATOR' where id = ?", user.id().value());
            assertThat(users.findByIdForUpdate(user.id()).orElseThrow().role()).isEqualTo(UserRole.MODERATOR);
            return null;
        });
    }

    @Test
    void lockForShareBlocksWritersButNotOtherSharers() {
        User user = storeUser("Test Person", UserRole.REGISTERED, community);

        inTransaction(() -> {
            assertThat(users.lockForShare(user.id())).isPresent();
            assertLockNotAvailable(user.id().value(), "update");
            assertThatCode(() -> tryLock(user.id().value(), "share")).doesNotThrowAnyException();
            return null;
        });
    }

    @Test
    void lockForShareIsEmptyForAnUnknownUser() {
        assertThat(inTransaction(() -> users.lockForShare(new UserId(UUID.randomUUID())))).isEmpty();
    }

    @Test
    void findAllByCommunityListsEveryUserOrderedByName() {
        Community other = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(2)));
        storeUser("Zoe Test", UserRole.REGISTERED, community);
        storeUser("Ana Test", UserRole.MODERATOR, community);
        storeUser("Other Person", UserRole.REGISTERED, other);

        List<User> found = users.findAllByCommunity(community.id());

        assertThat(found).extracting(user -> user.name().value()).containsExactly("Ana Test", "Zoe Test");
    }

    @Test
    void savedCommunitiesAreActiveByDefaultAndKeepTheirFlag() {
        Community inactive = communities.save(new Community(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(3), false));

        assertThat(communities.findById(community.id()).orElseThrow().active()).isTrue();
        assertThat(communities.findById(inactive.id()).orElseThrow().active()).isFalse();
    }

    @Test
    void setActiveTogglesTheProjectionFlag() {
        communities.setActive(community.id(), false);
        assertThat(communities.findById(community.id()).orElseThrow().active()).isFalse();

        communities.setActive(community.id(), true);
        assertThat(communities.findById(community.id()).orElseThrow().active()).isTrue();
    }

    @Test
    void renumberChangesTheNumber() {
        communities.renumber(community.id(), new CommunityNumber(44));

        assertThat(communities.findById(community.id()).orElseThrow().number()).isEqualTo(new CommunityNumber(44));
    }

    @Test
    void deleteRemovesAnEmptyCommunity() {
        communities.delete(community.id());

        assertThat(communities.findById(community.id())).isEmpty();
    }

    @Test
    void deleteIsTranslatedWhenTheCommunityStillHasUsers() {
        storeUser("Test Person", UserRole.REGISTERED, community);

        assertThatThrownBy(() -> communities.delete(community.id()))
                .isInstanceOfSatisfying(IdentityRuleViolation.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_HAS_USERS"));
        assertThat(communities.findById(community.id())).isPresent();
    }
}
