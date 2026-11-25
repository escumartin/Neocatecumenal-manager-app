package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.application.port.in.LogIn;
import io.github.escumartin.neocatecumenal.identity.application.port.in.LoginCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.LoginResult;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.LoginThrottleStore;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordHasher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle.IpState;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle.PairState;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class LoginThrottlePersistenceIT {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String IP = "10.20.30.40";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private LogIn logIn;

    @Autowired
    private LoginThrottleStore store;

    @Autowired
    private UserRepository users;

    @Autowired
    private CommunityRepository communities;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private PasswordHasher hasher;

    private Community community;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
        community = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(1)));
        users.save(User.register(new UserId(UUID.randomUUID()), new AccessId(100001), new DisplayName("Test User"),
                UserRole.REGISTERED, community.id(), hasher.hash(new PlainPassword(PASSWORD))));
        Mockito.clearInvocations(hasher);
    }

    private long verifications() {
        return Mockito.mockingDetails(hasher).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("matches"))
                .count();
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private List<LoginResult> concurrently(List<Callable<LoginResult>> attempts) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(attempts.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<LoginResult>> futures = new ArrayList<>();
        for (Callable<LoginResult> attempt : attempts) {
            futures.add(executor.submit(() -> {
                start.await();
                return attempt.call();
            }));
        }
        start.countDown();
        List<LoginResult> results = new ArrayList<>();
        for (Future<LoginResult> future : futures) {
            results.add(future.get(120, TimeUnit.SECONDS));
        }
        executor.shutdownNow();
        return results;
    }

    private LoginResult attempt(int accessId, String password, String ip) {
        return logIn.logIn(new LoginCommand(new AccessId(accessId), new PlainPassword(password), ip));
    }

    @Test
    void storedStatesAreReadBackUnderTheLock() {
        Instant first = Instant.parse("2026-03-01T10:00:00Z");
        Instant second = Instant.parse("2026-03-01T10:00:05Z");
        Instant blockedUntil = Instant.parse("2026-03-01T10:15:05Z");

        transactions.executeWithoutResult(status -> {
            store.lockIp("2001:db8::1");
            store.lockPair(new AccessId(123456), "2001:db8::1");
            store.save("2001:db8::1", new IpState(List.of(first, second), blockedUntil));
            store.save(new AccessId(123456), "2001:db8::1", new PairState(5, second, blockedUntil));
        });

        transactions.executeWithoutResult(status -> {
            assertThat(store.lockIp("2001:db8::1")).isEqualTo(new IpState(List.of(first, second), blockedUntil));
            assertThat(store.lockPair(new AccessId(123456), "2001:db8::1"))
                    .isEqualTo(new PairState(5, second, blockedUntil));
        });
    }

    @Test
    void unknownIpAndPairStartEmpty() {
        transactions.executeWithoutResult(status -> {
            assertThat(store.lockIp("192.0.2.9")).isEqualTo(IpState.empty());
            assertThat(store.lockPair(new AccessId(123457), "192.0.2.9")).isEqualTo(PairState.empty());
        });
    }

    @Test
    void ipWithZoneIdentifierIsStoredWithoutItAsItsSixtyFourBitPrefix() {
        transactions.executeWithoutResult(status -> {
            store.lockIp("fe80::1%eth0");
            store.save("fe80::1%eth0", new IpState(List.of(Instant.parse("2026-03-01T10:00:00Z")), null));
        });

        assertThat(jdbc.queryForObject("select count(*) from identity_login_ip_throttle where ip = 'fe80::'::inet",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void ipv6AddressesOfTheSameSixtyFourBitPrefixShareTheirIpState() {
        Instant failure = Instant.parse("2026-03-01T10:00:00Z");
        transactions.executeWithoutResult(status -> {
            store.lockIp("2001:db8:abcd:1::1");
            store.save("2001:db8:abcd:1::1", new IpState(List.of(failure), null));
        });

        transactions.executeWithoutResult(status -> {
            assertThat(store.lockIp("2001:db8:abcd:1:ffff:eeee:dddd:cccc"))
                    .isEqualTo(new IpState(List.of(failure), null));
            assertThat(store.lockIp("2001:db8:abcd:2::1")).isEqualTo(IpState.empty());
        });
    }

    @Test
    void ipv6AddressesOfTheSameSixtyFourBitPrefixShareTheirPairState() {
        Instant failure = Instant.parse("2026-03-01T10:00:00Z");
        transactions.executeWithoutResult(status -> {
            store.lockPair(new AccessId(123456), "2001:db8:abcd:1::1");
            store.save(new AccessId(123456), "2001:db8:abcd:1::1", new PairState(3, failure, null));
        });

        transactions.executeWithoutResult(status -> {
            assertThat(store.lockPair(new AccessId(123456), "2001:db8:abcd:1::2"))
                    .isEqualTo(new PairState(3, failure, null));
            assertThat(store.lockPair(new AccessId(123456), "2001:db8:abcd:2::1")).isEqualTo(PairState.empty());
        });
    }

    @Test
    void ipv4AddressesAreStoredUnchanged() {
        transactions.executeWithoutResult(status -> {
            store.lockIp("192.0.2.77");
            store.save("192.0.2.77", new IpState(List.of(Instant.parse("2026-03-01T10:00:00Z")), null));
        });

        assertThat(jdbc.queryForObject("select count(*) from identity_login_ip_throttle "
                + "where ip = '192.0.2.77'::inet", Long.class)).isEqualTo(1L);
    }

    @Test
    void lockingAnIpBlocksAnotherTransactionUntilTheFirstCommits() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
            store.lockIp("192.0.2.50");
            locked.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        locked.await();

        Future<?> waiter = executor.submit(() -> transactions.executeWithoutResult(status -> store.lockIp("192.0.2.50")));

        try {
            waiter.get(700, TimeUnit.MILLISECONDS);
            throw new AssertionError("the second transaction acquired the lock while the first held it");
        } catch (TimeoutException expected) {
            release.countDown();
        }
        holder.get(10, TimeUnit.SECONDS);
        waiter.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();
    }

    @Test
    void thirtySimultaneousWrongLoginsOfTheSamePairVerifyAtMostFiveTimes() throws Exception {
        List<Callable<LoginResult>> attempts = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            attempts.add(() -> attempt(100001, "wrong-password-value", IP));
        }

        List<LoginResult> results = concurrently(attempts);

        assertThat(verifications()).isLessThanOrEqualTo(5);
        assertThat(results).filteredOn(LoginResult.InvalidCredentials.class::isInstance).hasSize(5);
        assertThat(results).filteredOn(LoginResult.Blocked.class::isInstance).hasSize(25);
    }

    @Test
    void fortySimultaneousLoginsFromOneIpWithDifferentIdentifiersVerifyAtMostTwentyTimes() throws Exception {
        List<Callable<LoginResult>> attempts = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            int accessId = 500000 + i;
            attempts.add(() -> attempt(accessId, "wrong-password-value", IP));
        }

        List<LoginResult> results = concurrently(attempts);

        assertThat(verifications()).isLessThanOrEqualTo(20);
        assertThat(results).filteredOn(LoginResult.Blocked.class::isInstance).hasSize(20);
    }

    @Test
    void successfulLoginDeletesThePairRowAndDoesNotAddToTheIp() {
        attempt(100001, "wrong-password-value", IP);
        assertThat(count("identity_login_pair_throttle")).isEqualTo(1);

        LoginResult result = attempt(100001, PASSWORD, IP);

        assertThat(result).isInstanceOf(LoginResult.LoggedIn.class);
        assertThat(count("identity_login_pair_throttle")).isZero();
        assertThat(jdbc.queryForObject("select cardinality(recent_failures) from identity_login_ip_throttle",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void twentyFiveSimultaneousCorrectLoginsFromOneIpDoNotBlockIt() throws Exception {
        List<Callable<LoginResult>> attempts = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            attempts.add(() -> attempt(100001, PASSWORD, IP));
        }

        List<LoginResult> results = concurrently(attempts);

        assertThat(results).allMatch(LoginResult.LoggedIn.class::isInstance);
        assertThat(attempt(100001, PASSWORD, IP)).isInstanceOf(LoginResult.LoggedIn.class);
        assertThat(count("identity_session")).isEqualTo(Session.MAX_PER_USER);
    }

    @Test
    void simultaneousLoginsOfOneUserFromDifferentIpsNeverLeaveMoreThanTheSessionLimit() throws Exception {
        List<Callable<LoginResult>> attempts = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            String ip = "10.30.0." + (i + 1);
            attempts.add(() -> attempt(100001, PASSWORD, ip));
        }

        List<LoginResult> results = concurrently(attempts);

        assertThat(results).allMatch(LoginResult.LoggedIn.class::isInstance);
        assertThat(count("identity_session")).isLessThanOrEqualTo(Session.MAX_PER_USER);
    }
}
