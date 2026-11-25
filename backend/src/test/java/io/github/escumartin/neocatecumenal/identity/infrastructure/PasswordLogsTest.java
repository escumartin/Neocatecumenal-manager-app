package io.github.escumartin.neocatecumenal.identity.infrastructure;

import io.github.escumartin.neocatecumenal.identity.TestCommunityProvisionerConfig;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.IssuedSessionToken;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordHasher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestCommunityProvisionerConfig.class)
class PasswordLogsTest {

    private static final String ACTOR_NAME = "Qwerty Actorson";
    private static final String TARGET_NAME = "Zzyzx Quuxington";
    private static final int ACTOR_ACCESS_ID = 717171;
    private static final int TARGET_ACCESS_ID = 828282;
    private static final String ACTOR_PASSWORD = "Actor-Secret-Passphrase-1";
    private static final String TARGET_PASSWORD = "Target-Secret-Passphrase-2";
    private static final String NEW_PASSWORD = "Brand-New-Secret-Passphrase-3";
    private static final String WRONG_PASSWORD = "Wrong-Secret-Passphrase-4";

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
    private PasswordHasher hasher;

    @Autowired
    private JdbcTemplate jdbc;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger rootLogger;
    private Logger applicationLogger;
    private Level previousApplicationLevel;
    private User actor;
    private User target;
    private String actorBearer;
    private String targetBearer;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
        Community community = communities.save(new Community(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(5)));
        actor = users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(ACTOR_ACCESS_ID),
                new DisplayName(ACTOR_NAME), UserRole.ADMINISTRATOR, community.id(),
                hasher.hash(new PlainPassword(ACTOR_PASSWORD)), false, new PrivacyConsent("v1", Instant.now())));
        target = users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(TARGET_ACCESS_ID),
                new DisplayName(TARGET_NAME), UserRole.REGISTERED, community.id(),
                hasher.hash(new PlainPassword(TARGET_PASSWORD)), true, null));
        actorBearer = bearerFor(actor);
        targetBearer = bearerFor(target);
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        applicationLogger = (Logger) LoggerFactory.getLogger("io.github.escumartin.neocatecumenal");
        previousApplicationLevel = applicationLogger.getLevel();
        applicationLogger.setLevel(Level.TRACE);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
        appender.stop();
        applicationLogger.setLevel(previousApplicationLevel);
    }

    private String bearerFor(User user) {
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(user.id(), issued.hash(), Instant.now()));
        return "Bearer " + issued.token();
    }

    private String everythingLogged() {
        List<String> lines = new ArrayList<>();
        for (ILoggingEvent event : new ArrayList<>(appender.list)) {
            lines.add(event.getFormattedMessage());
            lines.add(String.valueOf(event.getKeyValuePairs()));
            lines.add(String.valueOf(event.getArgumentArray() == null ? "" : List.of(event.getArgumentArray())));
            lines.add(String.valueOf(event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage()));
        }
        return String.join("\n", lines);
    }

    private void changePassword(String bearer, String current, String next) throws Exception {
        mockMvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    @Test
    void changeResetAndNoticeLogNoPersonalDataOrCredentials() throws Exception {
        mockMvc.perform(post("/api/me/privacy-notice-acceptance").header(HttpHeaders.AUTHORIZATION, targetBearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"v1\"}"));
        changePassword(targetBearer, TARGET_PASSWORD, NEW_PASSWORD);
        changePassword(targetBearer, WRONG_PASSWORD, NEW_PASSWORD + "x");
        String reset = mockMvc.perform(post("/api/users/" + target.id().value() + "/password-reset")
                        .header(HttpHeaders.AUTHORIZATION, actorBearer))
                .andReturn().getResponse().getContentAsString();
        String initialPassword = JsonPath.read(reset, "$.initialPassword");
        mockMvc.perform(post("/api/me/privacy-notice-rejection").header(HttpHeaders.AUTHORIZATION, actorBearer));

        assertThat(initialPassword).isNotBlank();
        assertThat(everythingLogged())
                .doesNotContain(ACTOR_NAME)
                .doesNotContain(TARGET_NAME)
                .doesNotContain(String.valueOf(ACTOR_ACCESS_ID))
                .doesNotContain(String.valueOf(TARGET_ACCESS_ID))
                .doesNotContain(ACTOR_PASSWORD)
                .doesNotContain(TARGET_PASSWORD)
                .doesNotContain(NEW_PASSWORD)
                .doesNotContain(WRONG_PASSWORD)
                .doesNotContain(initialPassword)
                .doesNotContain(actorBearer)
                .doesNotContain(targetBearer)
                .doesNotContain("Bearer");
    }

    @Test
    void changeResetAndNoticeAreLoggedWithOpaqueIdentifiersOnly() throws Exception {
        mockMvc.perform(post("/api/me/privacy-notice-acceptance").header(HttpHeaders.AUTHORIZATION, targetBearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"v1\"}"));
        changePassword(targetBearer, TARGET_PASSWORD, NEW_PASSWORD);
        mockMvc.perform(post("/api/users/" + target.id().value() + "/password-reset")
                .header(HttpHeaders.AUTHORIZATION, actorBearer));
        mockMvc.perform(post("/api/me/privacy-notice-rejection").header(HttpHeaders.AUTHORIZATION, actorBearer));

        assertThat(everythingLogged())
                .contains("Aviso de datos aceptado")
                .contains("Contraseña cambiada por el propio usuario")
                .contains("Contraseña inicial reasignada")
                .contains("Aviso de datos rechazado")
                .contains(target.id().value().toString())
                .contains(actor.id().value().toString());
    }
}
