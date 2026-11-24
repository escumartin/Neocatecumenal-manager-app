package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle.IpState;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle.PairState;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence.CommunityPersistenceAdapter;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence.LoginThrottlePersistenceAdapter;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence.SessionPersistenceAdapter;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence.UserPersistenceAdapter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({LoginThrottlePersistenceAdapter.class, SessionPersistenceAdapter.class, UserPersistenceAdapter.class,
        CommunityPersistenceAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ThrottlePurgeIT {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private LoginThrottlePersistenceAdapter throttle;

    @Autowired
    private SessionPersistenceAdapter sessions;

    @Autowired
    private UserPersistenceAdapter users;

    @Autowired
    private CommunityPersistenceAdapter communities;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
    }

    private long pairRows() {
        return jdbc.queryForObject("select count(*) from identity_login_pair_throttle", Long.class);
    }

    private long ipRows() {
        return jdbc.queryForObject("select count(*) from identity_login_ip_throttle", Long.class);
    }

    private UserId newUser() {
        Community community = communities.save(new Community(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(1)));
        return users.save(User.register(new UserId(UUID.randomUUID()), new AccessId(123456), new DisplayName("Test"),
                UserRole.REGISTERED, community.id(), new PasswordHash("{argon2}x"))).id();
    }

    @Test
    void pairRowIsKeptUntilFifteenMinutesAfterTheLastFailure() {
        throttle.save(new AccessId(111111), "10.0.0.1", new PairState(2, T0, null));

        assertThat(throttle.purgeExpired(T0.plus(FIFTEEN_MINUTES).minusSeconds(1))).isZero();
        assertThat(pairRows()).isEqualTo(1);
        assertThat(throttle.purgeExpired(T0.plus(FIFTEEN_MINUTES))).isEqualTo(1);
        assertThat(pairRows()).isZero();
    }

    @Test
    void blockedPairRowIsKeptUntilTheBlockEnds() {
        throttle.save(new AccessId(111112), "10.0.0.1", new PairState(5, T0, T0.plus(Duration.ofMinutes(40))));

        assertThat(throttle.purgeExpired(T0.plus(Duration.ofMinutes(39)))).isZero();
        assertThat(throttle.purgeExpired(T0.plus(Duration.ofMinutes(40)))).isEqualTo(1);
    }

    @Test
    void ipRowIsKeptUntilItsLastFailureLeavesTheWindowAndItsBlockEnds() {
        throttle.save("10.0.0.2", new IpState(List.of(T0, T0.plusSeconds(60)), T0.plus(Duration.ofMinutes(30))));

        assertThat(throttle.purgeExpired(T0.plus(Duration.ofMinutes(29)))).isZero();
        assertThat(ipRows()).isEqualTo(1);
        assertThat(throttle.purgeExpired(T0.plus(Duration.ofMinutes(30)))).isEqualTo(1);
        assertThat(ipRows()).isZero();
    }

    @Test
    void unblockedIpRowWithoutRecentFailuresIsPurged() {
        throttle.save("10.0.0.3", IpState.empty());

        assertThat(throttle.purgeExpired(T0)).isEqualTo(1);
    }

    @Test
    void ipRowWithRecentFailuresIsKeptEvenWhenNotBlocked() {
        throttle.save("10.0.0.4", new IpState(List.of(T0), null));

        assertThat(throttle.purgeExpired(T0.plusSeconds(60))).isZero();
        assertThat(ipRows()).isEqualTo(1);
    }

    @Test
    void expiredSessionsAreRemovedAndValidOnesKept() {
        UserId owner = newUser();
        Instant now = T0.plus(Duration.ofDays(100));
        sessions.save(new Session(UUID.randomUUID(), owner, new byte[] {1}, now.minus(Duration.ofDays(31)),
                now.minus(Duration.ofDays(30))));
        sessions.save(new Session(UUID.randomUUID(), owner, new byte[] {2}, now.minus(Duration.ofDays(90)),
                now.minus(Duration.ofMinutes(1))));
        sessions.save(new Session(UUID.randomUUID(), owner, new byte[] {3}, now.minus(Duration.ofDays(89)),
                now.minus(Duration.ofDays(29))));

        int removed = sessions.purgeExpired(now);

        assertThat(removed).isEqualTo(2);
        assertThat(sessions.findByTokenHash(new byte[] {1})).isEmpty();
        assertThat(sessions.findByTokenHash(new byte[] {2})).isEmpty();
        assertThat(sessions.findByTokenHash(new byte[] {3})).isPresent();
    }
}
