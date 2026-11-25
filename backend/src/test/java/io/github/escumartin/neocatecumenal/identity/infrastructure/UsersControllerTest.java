package io.github.escumartin.neocatecumenal.identity.infrastructure;

import io.github.escumartin.neocatecumenal.identity.TestCommunityProvisionerConfig;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.TestUsers;
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
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestCommunityProvisionerConfig.class)
class UsersControllerTest {

    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(200000);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

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
    private JdbcTemplate jdbc;

    private Community communityA;
    private Community communityB;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
        communityA = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(1)));
        communityB = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(2)));
    }

    private User user(UserRole role, Community community) {
        return user(role, community, "Test Person");
    }

    private User user(UserRole role, Community community, String name) {
        return users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(ACCESS_IDS.incrementAndGet()),
                new DisplayName(name), role, community.id(), new PasswordHash("{argon2}hash"), false,
                new PrivacyConsent("v1", Instant.now())));
    }

    private String bearerFor(User user) {
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(user.id(), issued.hash(), Instant.now()));
        return "Bearer " + issued.token();
    }

    private static String json(String name, String role, UUID communityId) {
        String community = communityId == null ? "" : ",\"communityId\":\"" + communityId + "\"";
        return "{\"name\":\"" + name + "\",\"role\":\"" + role + "\"" + community + "}";
    }

    private MockHttpServletRequestBuilder asJson(MockHttpServletRequestBuilder builder, String bearer,
            String body) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private int countUsers() {
        return jdbc.queryForObject("select count(*) from identity_user", Integer.class);
    }

    @Test
    void moderatorCreatesARegisteredUserAndSeesTheInitialPasswordOnce() throws Exception {
        String bearer = bearerFor(user(UserRole.MODERATOR, communityA));

        String body = mockMvc.perform(asJson(post("/api/users"), bearer,
                        json("Ana Lopez", "REGISTERED", communityA.id().value())))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/users/")))
                .andExpect(jsonPath("$.name").value("Ana Lopez"))
                .andExpect(jsonPath("$.role").value("REGISTERED"))
                .andExpect(jsonPath("$.communityId").value(communityA.id().value().toString()))
                .andExpect(jsonPath("$.passwordChangePending").value(true))
                .andExpect(jsonPath("$.accessId").value(greaterThanOrEqualTo(100000)))
                .andExpect(jsonPath("$.initialPassword").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.id");
        assertThat(jdbc.queryForObject("select role from identity_user where id = ?::uuid", String.class, id))
                .isEqualTo("REGISTERED");
        assertThat(jdbc.queryForObject("select password_hash from identity_user where id = ?::uuid", String.class,
                id)).doesNotContain((String) JsonPath.read(body, "$.initialPassword"));
        mockMvc.perform(get("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialPassword").doesNotExist());
    }

    @Test
    void createdUserCanLogInWithTheInitialPassword() throws Exception {
        String bearer = bearerFor(user(UserRole.ADMINISTRATOR, communityA));
        String body = mockMvc.perform(asJson(post("/api/users"), bearer,
                        json("Ana Lopez", "MODERATOR", communityB.id().value())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int accessId = JsonPath.read(body, "$.accessId");
        String password = JsonPath.read(body, "$.initialPassword");

        mockMvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessId\":" + accessId + ",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.me.pendingSteps", hasSize(2)));
    }

    @Test
    void createWithInvalidDataReturnsFieldCodesAndCreatesNothing() throws Exception {
        String bearer = bearerFor(user(UserRole.ADMINISTRATOR, communityA));
        int before = countUsers();

        mockMvc.perform(asJson(post("/api/users"), bearer, json("", "REGISTERED", communityA.id().value())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        mockMvc.perform(asJson(post("/api/users"), bearer, json("   ", "REGISTERED", communityA.id().value())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        mockMvc.perform(asJson(post("/api/users"), bearer,
                        json("x".repeat(101), "REGISTERED", communityA.id().value())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
        mockMvc.perform(asJson(post("/api/users"), bearer, json("Ana", "REGISTERED", null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("communityId"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        mockMvc.perform(asJson(post("/api/users"), bearer, json("Ana", "REGISTERED", UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("communityId"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_NOT_FOUND"));
        mockMvc.perform(asJson(post("/api/users"), bearer, json("Ana", "KING", communityA.id().value())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));

        assertThat(countUsers()).isEqualTo(before);
    }

    @Test
    void aNumericNameIsAValidationProblemAndCreatesNothing() throws Exception {
        String bearer = bearerFor(user(UserRole.ADMINISTRATOR, communityA));
        int before = countUsers();

        mockMvc.perform(asJson(post("/api/users"), bearer,
                        "{\"name\":12345,\"role\":\"REGISTERED\",\"communityId\":\"" + communityA.id().value()
                                + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(countUsers()).isEqualTo(before);
    }

    @Test
    void moderatorCreatingAModeratorOrInAnotherCommunityIsForbidden() throws Exception {
        String bearer = bearerFor(user(UserRole.MODERATOR, communityA));
        int before = countUsers();

        mockMvc.perform(asJson(post("/api/users"), bearer, json("Ana", "MODERATOR", communityA.id().value())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(asJson(post("/api/users"), bearer, json("Ana", "REGISTERED", communityB.id().value())))
                .andExpect(status().isForbidden());

        assertThat(countUsers()).isEqualTo(before);
    }

    @Test
    void createWithoutSessionIsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana", "REGISTERED", communityA.id().value())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void thereIsNoSelfRegistrationRoute() throws Exception {
        for (String path : new String[] {"/api/users/register", "/api/register", "/api/signup", "/api/sessions/new"}) {
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                            .content(json("Ana", "REGISTERED", communityA.id().value())))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void callerWithPendingFirstAccessStepsCannotCreateUsers() throws Exception {
        User pending = users.save(User.register(new UserId(UUID.randomUUID()),
                new AccessId(ACCESS_IDS.incrementAndGet()), new DisplayName("Pending"), UserRole.ADMINISTRATOR,
                communityA.id(), new PasswordHash("{argon2}hash")));

        mockMvc.perform(asJson(post("/api/users"), bearerFor(pending),
                        json("Ana", "REGISTERED", communityA.id().value())))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorRenamesARegisteredUserOfTheirCommunity() throws Exception {
        String bearer = bearerFor(user(UserRole.MODERATOR, communityA));
        User target = user(UserRole.REGISTERED, communityA);

        mockMvc.perform(asJson(put("/api/users/" + target.id().value()), bearer,
                        json("New Name", "REGISTERED", communityA.id().value())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.id").value(target.id().value().toString()));
        assertThat(jdbc.queryForObject("select name from identity_user where id = ?", String.class,
                target.id().value())).isEqualTo("New Name");
    }

    @Test
    void moderatorChangingRoleOrCommunityIsForbidden() throws Exception {
        String bearer = bearerFor(user(UserRole.MODERATOR, communityA));
        User target = user(UserRole.REGISTERED, communityA);

        mockMvc.perform(asJson(put("/api/users/" + target.id().value()), bearer,
                        json("Same", "MODERATOR", communityA.id().value())))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(put("/api/users/" + target.id().value()), bearer,
                        json("Same", "REGISTERED", communityB.id().value())))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorChangesRoleAndCommunity() throws Exception {
        String bearer = bearerFor(user(UserRole.ADMINISTRATOR, communityA));
        User target = user(UserRole.REGISTERED, communityA);

        mockMvc.perform(asJson(put("/api/users/" + target.id().value()), bearer,
                        json("Same", "MODERATOR", communityB.id().value())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"))
                .andExpect(jsonPath("$.communityId").value(communityB.id().value().toString()));
    }

    @Test
    void updateOfAMissingUserIsNotFoundAndInvalidBodyIsUnprocessable() throws Exception {
        String bearer = bearerFor(user(UserRole.ADMINISTRATOR, communityA));
        User target = user(UserRole.REGISTERED, communityA);

        mockMvc.perform(asJson(put("/api/users/" + UUID.randomUUID()), bearer,
                        json("Same", "REGISTERED", communityA.id().value())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(asJson(put("/api/users/" + target.id().value()), bearer,
                        json("", "REGISTERED", communityA.id().value())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void administratorChangingTheirOwnRoleConflicts() throws Exception {
        User administrator = user(UserRole.ADMINISTRATOR, communityA);
        user(UserRole.ADMINISTRATOR, communityA);

        mockMvc.perform(asJson(put("/api/users/" + administrator.id().value()), bearerFor(administrator),
                        json("Same", "MODERATOR", communityA.id().value())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));
    }

    @Test
    void demotingOrDeletingTheLastModeratorConflicts() throws Exception {
        String bearer = bearerFor(user(UserRole.ADMINISTRATOR, communityB));
        User moderator = user(UserRole.MODERATOR, communityA);

        mockMvc.perform(asJson(put("/api/users/" + moderator.id().value()), bearer,
                        json("Same", "REGISTERED", communityA.id().value())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_MODERATOR"));
        mockMvc.perform(delete("/api/users/" + moderator.id().value()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_MODERATOR"));
        assertThat(users.findById(moderator.id())).isPresent();
    }

    @Test
    void nobodyDeletesThemselves() throws Exception {
        User administrator = user(UserRole.ADMINISTRATOR, communityA);

        mockMvc.perform(delete("/api/users/" + administrator.id().value())
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(administrator)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));
    }

    @Test
    void deleteRemovesTheUserAndItsSessionStopsWorking() throws Exception {
        String moderator = bearerFor(user(UserRole.MODERATOR, communityA));
        User target = user(UserRole.REGISTERED, communityA);
        String targetBearer = bearerFor(target);
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, targetBearer)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/" + target.id().value()).header(HttpHeaders.AUTHORIZATION, moderator))
                .andExpect(status().isNoContent());

        assertThat(users.findById(target.id())).isEmpty();
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, targetBearer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void moderatorCannotDeleteAUserOfAnotherCommunityAndMissingUsersAreNotFound() throws Exception {
        String bearer = bearerFor(user(UserRole.MODERATOR, communityA));
        User other = user(UserRole.REGISTERED, communityB);

        mockMvc.perform(delete("/api/users/" + other.id().value()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
        assertThat(users.findById(other.id())).isPresent();
    }

    @Test
    void registeredUserCannotManageUsers() throws Exception {
        User registered = user(UserRole.REGISTERED, communityA);
        String bearer = bearerFor(registered);

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/" + registered.id().value()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/" + registered.id().value()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReturnsAUserOfTheCallersCommunityAndForbidsAnotherCommunity() throws Exception {
        String bearer = bearerFor(user(UserRole.MODERATOR, communityA));
        User own = user(UserRole.REGISTERED, communityA);
        User other = user(UserRole.REGISTERED, communityB);

        mockMvc.perform(get("/api/users/" + own.id().value()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(own.id().value().toString()))
                .andExpect(jsonPath("$.accessId").value(own.accessId().value()))
                .andExpect(jsonPath("$.passwordChangePending").value(false));
        mockMvc.perform(get("/api/users/" + other.id().value()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void moderatorListIsLimitedToTheirCommunityAndOrderedByName() throws Exception {
        String bearer = bearerFor(user(UserRole.MODERATOR, communityA, "Zoe"));
        user(UserRole.REGISTERED, communityA, "Alba");
        user(UserRole.REGISTERED, communityB, "Other");

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].name").value("Alba"))
                .andExpect(jsonPath("$.items[1].name").value("Zoe"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50));
        mockMvc.perform(get("/api/users").param("communityId", communityB.id().value().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorListsAChosenCommunityWithPaging() throws Exception {
        String bearer = bearerFor(user(UserRole.ADMINISTRATOR, communityA));
        user(UserRole.REGISTERED, communityB, "Alba");
        user(UserRole.REGISTERED, communityB, "Berta");
        user(UserRole.REGISTERED, communityB, "Carla");

        mockMvc.perform(get("/api/users").param("communityId", communityB.id().value().toString())
                        .param("page", "1").param("size", "2").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Carla"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2));
    }

    @Test
    void administratorMustPassACommunityAndPagingIsBounded() throws Exception {
        String bearer = bearerFor(user(UserRole.ADMINISTRATOR, communityA));

        mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("communityId"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_REQUIRED"));
        mockMvc.perform(get("/api/users").param("communityId", communityA.id().value().toString())
                        .param("size", "101").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        mockMvc.perform(get("/api/users").param("communityId", communityA.id().value().toString())
                        .param("page", "-1").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
        mockMvc.perform(get("/api/users").param("communityId", "not-a-uuid").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("communityId"));
    }

    @Test
    void listWithoutSessionIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }
}
