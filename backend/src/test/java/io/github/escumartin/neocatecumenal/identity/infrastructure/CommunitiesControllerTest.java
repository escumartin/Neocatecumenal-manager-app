package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CommunitiesControllerTest {

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

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
    }

    private Community community(int number) {
        return communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(number)));
    }

    private String bearerFor(UserRole role, Community community, PrivacyConsent consent, boolean passwordPending) {
        User user = users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(100000 + (int) (Math.random() * 800000)),
                new DisplayName("Test Person"), role, community.id(), new PasswordHash("{argon2}hash"),
                passwordPending, consent));
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(user.id(), issued.hash(), Instant.now()));
        return "Bearer " + issued.token();
    }

    private String readyBearerFor(UserRole role, Community community) {
        return bearerFor(role, community, new PrivacyConsent("v1", Instant.now()), false);
    }

    @Test
    void listsCommunitiesOrderedByNumber() throws Exception {
        Community twelve = community(12);
        Community three = community(3);
        Community twenty = community(20);

        mockMvc.perform(get("/api/communities")
                        .header(HttpHeaders.AUTHORIZATION, readyBearerFor(UserRole.REGISTERED, twelve)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].number").value(3))
                .andExpect(jsonPath("$[0].id").value(three.id().value().toString()))
                .andExpect(jsonPath("$[1].number").value(12))
                .andExpect(jsonPath("$[2].number").value(20))
                .andExpect(jsonPath("$[2].id").value(twenty.id().value().toString()));
    }

    @Test
    void listsNothingWhenThereAreNoOtherCommunities() throws Exception {
        Community only = community(1);

        mockMvc.perform(get("/api/communities")
                        .header(HttpHeaders.AUTHORIZATION, readyBearerFor(UserRole.ADMINISTRATOR, only)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void visitorGetsUnauthenticatedProblem() throws Exception {
        mockMvc.perform(get("/api/communities"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void userWithPendingFirstAccessStepsIsForbidden() throws Exception {
        Community community = community(5);

        mockMvc.perform(get("/api/communities")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearerFor(UserRole.REGISTERED, community, null, true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVACY_NOTICE_PENDING"));
    }
}
