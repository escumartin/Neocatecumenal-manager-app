package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import io.github.escumartin.neocatecumenal.identity.Role;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(PermissionsIT.ProtectedActions.class)
class PermissionsIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(100000);

    @TestConfiguration
    static class ProtectedActions {

        @RestController
        @RequestMapping("/api/test")
        static class Controller {

            private final IdentityApi identityApi;

            Controller(IdentityApi identityApi) {
                this.identityApi = identityApi;
            }

            @GetMapping("/moderator-action")
            ResponseEntity<Void> moderatorAction() {
                boolean allowed = identityApi.currentUser()
                        .map(current -> current.role().includes(Role.MODERATOR))
                        .orElse(false);
                return ResponseEntity.status(allowed ? HttpStatus.NO_CONTENT : HttpStatus.FORBIDDEN).build();
            }
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
        return users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(ACCESS_IDS.incrementAndGet()),
                new DisplayName("Test Person"), role, community.id(), new PasswordHash("{argon2}hash"), false,
                new PrivacyConsent("v1", Instant.now())));
    }

    private String bearerFor(User user) {
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(user.id(), issued.hash(), Instant.now()));
        return "Bearer " + issued.token();
    }

    @Test
    void registeredUserIsDeniedAModeratorAction() throws Exception {
        String bearer = bearerFor(user(UserRole.REGISTERED, communityA));

        mockMvc.perform(get("/api/test/moderator-action").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorAndAdministratorInheritTheModeratorAction() throws Exception {
        String moderator = bearerFor(user(UserRole.MODERATOR, communityA));
        String administrator = bearerFor(user(UserRole.ADMINISTRATOR, communityA));

        mockMvc.perform(get("/api/test/moderator-action").header(HttpHeaders.AUTHORIZATION, moderator))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/test/moderator-action").header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isNoContent());
    }

    @Test
    void moderatorActsOnARegisteredUserOfTheirOwnCommunity() throws Exception {
        String bearer = bearerFor(user(UserRole.MODERATOR, communityA));
        User target = user(UserRole.REGISTERED, communityA);

        mockMvc.perform(get("/api/users/" + target.id().value()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    @Test
    void moderatorOnAUserOfAnotherCommunityIsAlwaysForbidden() throws Exception {
        String bearer = bearerFor(user(UserRole.MODERATOR, communityA));
        User target = user(UserRole.REGISTERED, communityB);

        mockMvc.perform(get("/api/users/" + target.id().value()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderatorOnAUserThatDoesNotExistGetsNotFound() throws Exception {
        String bearer = bearerFor(user(UserRole.MODERATOR, communityA));

        mockMvc.perform(get("/api/users/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void administratorActsOnUsersOfAnyCommunity() throws Exception {
        String bearer = bearerFor(user(UserRole.ADMINISTRATOR, communityA));
        User inA = user(UserRole.REGISTERED, communityA);
        User inB = user(UserRole.MODERATOR, communityB);

        mockMvc.perform(get("/api/users/" + inA.id().value()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/" + inB.id().value()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    @Test
    void roleChangeAppliesOnTheNextRequestWithTheSameSession() throws Exception {
        User promoted = user(UserRole.REGISTERED, communityA);
        String bearer = bearerFor(promoted);

        mockMvc.perform(get("/api/test/moderator-action").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());

        users.save(User.restore(promoted.id(), promoted.accessId(), promoted.name(), UserRole.MODERATOR,
                promoted.communityId(), promoted.passwordHash(), false, promoted.privacyConsent().orElseThrow(),
                promoted.version()));

        mockMvc.perform(get("/api/test/moderator-action").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
    }

    @Test
    void communityChangeAppliesOnTheNextRequestWithTheSameSession() throws Exception {
        User moderator = user(UserRole.MODERATOR, communityA);
        String bearer = bearerFor(moderator);
        User target = user(UserRole.REGISTERED, communityB);

        mockMvc.perform(get("/api/users/" + target.id().value()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());

        users.save(User.restore(moderator.id(), moderator.accessId(), moderator.name(), moderator.role(),
                communityB.id(), moderator.passwordHash(), false, moderator.privacyConsent().orElseThrow(),
                moderator.version()));

        mockMvc.perform(get("/api/users/" + target.id().value()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    @Test
    void visitorIsUnauthenticatedOnProtectedActions() throws Exception {
        mockMvc.perform(get("/api/test/moderator-action"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
