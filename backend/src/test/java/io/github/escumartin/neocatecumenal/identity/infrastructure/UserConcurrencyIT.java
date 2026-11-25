package io.github.escumartin.neocatecumenal.identity.infrastructure;

import io.github.escumartin.neocatecumenal.identity.TestCommunityProvisionerConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.UserDeleted;
import io.github.escumartin.neocatecumenal.identity.application.port.out.AccessIdGenerator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.IssuedSessionToken;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import({UserConcurrencyIT.Probes.class, TestCommunityProvisionerConfig.class})
class UserConcurrencyIT {

    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(400000);
    private static final int ROUNDS = 5;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static final class ScriptedAccessIds implements AccessIdGenerator {

        private final Deque<Integer> values = new ArrayDeque<>();

        synchronized void script(Integer... scripted) {
            values.clear();
            values.addAll(List.of(scripted));
        }

        @Override
        public synchronized AccessId next() {
            return new AccessId(values.removeFirst());
        }
    }

    static final class DeletedEvents {

        final List<UserDeleted> received = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(UserDeleted event) {
            received.add(event);
        }
    }

    @TestConfiguration
    static class Probes {

        @Bean
        @Primary
        ScriptedAccessIds scriptedAccessIds() {
            return new ScriptedAccessIds();
        }

        @Bean
        DeletedEvents deletedEvents() {
            return new DeletedEvents();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private CommunityRepository communities;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private SessionTokenIssuer tokens;

    @Autowired
    private ScriptedAccessIds scriptedAccessIds;

    @Autowired
    private DeletedEvents deletedEvents;

    @Autowired
    private JdbcTemplate jdbc;

    private Community communityA;
    private Community communityB;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
        communityA = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(1)));
        communityB = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(2)));
        deletedEvents.received.clear();
    }

    private User user(UserRole role, Community community) {
        return users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(ACCESS_IDS.incrementAndGet()),
                new DisplayName("Test Person"), role, community.id(), new PasswordHash("{argon2}hash"), false,
                new PrivacyConsent("v1", Instant.now())));
    }

    private String bearerFor(User user) {
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(user.id(), issued.hash(), Instant.now()));
        return "Bearer " + issued.token();
    }

    private MockHttpServletRequestBuilder asJson(MockHttpServletRequestBuilder builder, String bearer,
            String body) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String json(String role, Community community) {
        return "{\"name\":\"Same Name\",\"role\":\"" + role + "\",\"communityId\":\"" + community.id().value()
                + "\"}";
    }

    private List<MvcResult> runTogether(List<Callable<MvcResult>> requests) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        CyclicBarrier barrier = new CyclicBarrier(requests.size());
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> request : requests) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return request.call();
                }));
            }
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private int countRole(UserRole role) {
        return jdbc.queryForObject("select count(*) from identity_user where role = ?", Integer.class, role.name());
    }

    @Test
    void twoCreationsDrawingTheSameAccessIdBothSucceedWithDistinctIdentifiers() throws Exception {
        String bearer = bearerFor(user(UserRole.ADMINISTRATOR, communityA));
        scriptedAccessIds.script(555555, 555555, 666666, 777777);
        String body = "{\"name\":\"Ana Lopez\",\"role\":\"REGISTERED\",\"communityId\":\""
                + communityA.id().value() + "\"}";

        List<MvcResult> results = runTogether(List.of(
                () -> mockMvc.perform(asJson(post("/api/users"), bearer, body)).andReturn(),
                () -> mockMvc.perform(asJson(post("/api/users"), bearer, body)).andReturn()));

        assertThat(results).extracting(result -> result.getResponse().getStatus()).containsExactly(201, 201);
        List<Integer> accessIds = results.stream()
                .map(result -> (Integer) JsonPath.read(contentOf(result), "$.accessId")).toList();
        assertThat(accessIds).containsExactlyInAnyOrder(555555, 666666);
        assertThat(jdbc.queryForObject("select count(distinct access_id) from identity_user where role = 'REGISTERED'",
                Integer.class)).isEqualTo(2);
    }

    private static String contentOf(MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void simultaneousDemotionsOfTheLastModeratorsLeaveOneAndFailOne() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            setUp();
            String firstAdmin = bearerFor(user(UserRole.ADMINISTRATOR, communityB));
            String secondAdmin = bearerFor(user(UserRole.ADMINISTRATOR, communityB));
            User firstModerator = user(UserRole.MODERATOR, communityA);
            User secondModerator = user(UserRole.MODERATOR, communityA);

            List<MvcResult> results = runTogether(List.of(
                    () -> mockMvc.perform(asJson(put("/api/users/" + firstModerator.id().value()), firstAdmin,
                            json("REGISTERED", communityA))).andReturn(),
                    () -> mockMvc.perform(asJson(put("/api/users/" + secondModerator.id().value()), secondAdmin,
                            json("REGISTERED", communityA))).andReturn()));

            assertThat(results).extracting(result -> result.getResponse().getStatus())
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("select count(*) from identity_user where role = 'MODERATOR' "
                    + "and community_id = ?", Integer.class, communityA.id().value())).isEqualTo(1);
        }
    }

    @Test
    void simultaneousDeletionsOfTheLastModeratorsLeaveOneAndFailOne() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            setUp();
            String firstAdmin = bearerFor(user(UserRole.ADMINISTRATOR, communityB));
            String secondAdmin = bearerFor(user(UserRole.ADMINISTRATOR, communityB));
            User firstModerator = user(UserRole.MODERATOR, communityA);
            User secondModerator = user(UserRole.MODERATOR, communityA);

            List<MvcResult> results = runTogether(List.of(
                    () -> mockMvc.perform(delete("/api/users/" + firstModerator.id().value())
                            .header(HttpHeaders.AUTHORIZATION, firstAdmin)).andReturn(),
                    () -> mockMvc.perform(delete("/api/users/" + secondModerator.id().value())
                            .header(HttpHeaders.AUTHORIZATION, secondAdmin)).andReturn()));

            assertThat(results).extracting(result -> result.getResponse().getStatus())
                    .containsExactlyInAnyOrder(204, 409);
            assertThat(countRole(UserRole.MODERATOR)).isEqualTo(1);
        }
    }

    @Test
    void administratorsDemotingEachOtherSimultaneouslyNeverLeaveTheSystemWithoutOne() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            setUp();
            User first = user(UserRole.ADMINISTRATOR, communityA);
            User second = user(UserRole.ADMINISTRATOR, communityA);
            String firstBearer = bearerFor(first);
            String secondBearer = bearerFor(second);

            List<MvcResult> results = runTogether(List.of(
                    () -> mockMvc.perform(asJson(put("/api/users/" + second.id().value()), firstBearer,
                            json("REGISTERED", communityA))).andReturn(),
                    () -> mockMvc.perform(asJson(put("/api/users/" + first.id().value()), secondBearer,
                            json("REGISTERED", communityA))).andReturn()));

            assertThat(results).extracting(result -> result.getResponse().getStatus()).containsOnlyOnce(200);
            assertThat(countRole(UserRole.ADMINISTRATOR)).isEqualTo(1);
        }
    }

    @Test
    void deletingAUserCascadesItsSessionsAndPublishesUserDeletedAfterCommit() throws Exception {
        String admin = bearerFor(user(UserRole.ADMINISTRATOR, communityA));
        User target = user(UserRole.REGISTERED, communityA);
        bearerFor(target);
        bearerFor(target);
        assertThat(jdbc.queryForObject("select count(*) from identity_session where user_id = ?", Integer.class,
                target.id().value())).isEqualTo(2);

        mockMvc.perform(delete("/api/users/" + target.id().value()).header(HttpHeaders.AUTHORIZATION, admin))
                .andReturn();

        assertThat(jdbc.queryForObject("select count(*) from identity_session where user_id = ?", Integer.class,
                target.id().value())).isZero();
        assertThat(deletedEvents.received).containsExactly(new UserDeleted(target.id().value()));
    }

    @Test
    void rejectedDeletionPublishesNothing() throws Exception {
        String admin = bearerFor(user(UserRole.ADMINISTRATOR, communityB));
        User lastModerator = user(UserRole.MODERATOR, communityA);

        mockMvc.perform(delete("/api/users/" + lastModerator.id().value()).header(HttpHeaders.AUTHORIZATION, admin))
                .andReturn();

        assertThat(deletedEvents.received).isEmpty();
        assertThat(users.findById(lastModerator.id())).isPresent();
    }
}
