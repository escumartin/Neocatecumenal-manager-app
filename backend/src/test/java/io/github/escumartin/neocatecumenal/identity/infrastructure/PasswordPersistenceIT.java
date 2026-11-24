package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.IssuedSessionToken;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordHasher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.CommonPasswords;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PasswordPersistenceIT {

    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(300000);
    private static final String INITIAL = "initial-Passphrase-7";
    private static final String CHOSEN = "Chosen-Passphrase-42";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

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
    private PasswordHasher hasher;

    @Autowired
    private CommonPasswords commonPasswords;

    @Autowired
    private JdbcTemplate jdbc;

    private Community community;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
        community = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(1)));
    }

    private User userWithPassword(UserRole role, String password, boolean passwordChangeRequired) {
        return users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(ACCESS_IDS.incrementAndGet()),
                new DisplayName("Test Person"), role, community.id(), hasher.hash(new PlainPassword(password)),
                passwordChangeRequired, new PrivacyConsent("v1", Instant.now())));
    }

    private String bearerFor(User user) {
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(user.id(), issued.hash(), Instant.now()));
        return "Bearer " + issued.token();
    }

    private String storedHash(UserId id) {
        return jdbc.queryForObject("select password_hash from identity_user where id = ?::uuid", String.class,
                id.value().toString());
    }

    @Test
    void commonPasswordsComeFromTheBundledLists() {
        assertThat(commonPasswords.contains("password")).isTrue();
        assertThat(commonPasswords.contains("12345678")).isTrue();
    }

    @Test
    void commonPasswordsIncludeTheCuratedSpanishList() {
        assertThat(commonPasswords.contains("contraseña123")).isTrue();
        assertThat(commonPasswords.contains("neocatecumenal")).isTrue();
        assertThat(commonPasswords.contains("parroquia123")).isTrue();
    }

    @Test
    void commonPasswordsLookupIsExactAndUnknownEntriesAreNotCommon() {
        assertThat(commonPasswords.contains("passwor")).isFalse();
        assertThat(commonPasswords.contains("x7-tpq-kz2-wm9")).isFalse();
        assertThat(commonPasswords.contains("")).isFalse();
    }

    @Test
    void changingTheOwnPasswordStoresAnArgon2HashAndNeverThePassword() throws Exception {
        User user = userWithPassword(UserRole.REGISTERED, INITIAL, false);

        mockMvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, bearerFor(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + INITIAL + "\",\"newPassword\":\"" + CHOSEN + "\"}"))
                .andExpect(status().isNoContent());

        String hash = storedHash(user.id());
        assertThat(hash).startsWith("{argon2}").doesNotContain(CHOSEN).doesNotContain(INITIAL);
        assertThat(hasher.matches(new PlainPassword(CHOSEN), users.findById(user.id()).orElseThrow().passwordHash()))
                .isTrue();
    }

    @Test
    void anAssignedInitialPasswordIsStoredAsAnArgon2HashAndNeverInTheClear() throws Exception {
        User moderator = userWithPassword(UserRole.MODERATOR, INITIAL, false);
        User target = userWithPassword(UserRole.REGISTERED, INITIAL, false);

        String body = mockMvc.perform(post("/api/users/" + target.id().value() + "/password-reset")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(moderator)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String initialPassword = JsonPath.read(body, "$.initialPassword");
        assertThat(storedHash(target.id())).startsWith("{argon2}").doesNotContain(initialPassword);
    }

    @Test
    void aCreatedUserGetsAnArgon2HashAndNeverTheInitialPassword() throws Exception {
        User moderator = userWithPassword(UserRole.MODERATOR, INITIAL, false);

        String body = mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, bearerFor(moderator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Person\",\"role\":\"REGISTERED\",\"communityId\":\""
                                + community.id().value() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.id");
        String initialPassword = JsonPath.read(body, "$.initialPassword");
        assertThat(storedHash(new UserId(UUID.fromString(id)))).startsWith("{argon2}")
                .doesNotContain(initialPassword);
    }
}
