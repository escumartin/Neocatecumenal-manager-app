package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class UserManagementLogsTest {

    private static final String ACTOR_NAME = "Qwerty Actorson";
    private static final String CREATED_NAME = "Zzyzx Quuxington";
    private static final String RENAMED = "Plugh Xyzzyman";
    private static final int ACTOR_ACCESS_ID = 313131;

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

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger rootLogger;
    private Logger applicationLogger;
    private Level previousApplicationLevel;
    private Community community;
    private String bearer;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
        community = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(5)));
        User actor = users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(ACTOR_ACCESS_ID),
                new DisplayName(ACTOR_NAME), UserRole.ADMINISTRATOR, community.id(), new PasswordHash("{argon2}h"),
                false, new PrivacyConsent("v1", Instant.now())));
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(actor.id(), issued.hash(), Instant.now()));
        bearer = "Bearer " + issued.token();
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

    private String body(String name, String role) {
        return "{\"name\":\"" + name + "\",\"role\":\"" + role + "\",\"communityId\":\""
                + community.id().value() + "\"}";
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

    @Test
    void createUpdateAndDeleteLogNoPersonalDataOrCredentials() throws Exception {
        String created = mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body(CREATED_NAME, "REGISTERED")))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        int accessId = JsonPath.read(created, "$.accessId");
        String initialPassword = JsonPath.read(created, "$.initialPassword");
        mockMvc.perform(put("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body(RENAMED, "REGISTERED")));
        mockMvc.perform(delete("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, bearer));

        assertThat(everythingLogged())
                .doesNotContain(CREATED_NAME)
                .doesNotContain(RENAMED)
                .doesNotContain(ACTOR_NAME)
                .doesNotContain(String.valueOf(accessId))
                .doesNotContain(String.valueOf(ACTOR_ACCESS_ID))
                .doesNotContain(initialPassword)
                .doesNotContain(bearer)
                .doesNotContain("Bearer");
    }

    @Test
    void createUpdateAndDeleteAreLogged() throws Exception {
        String created = mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body(CREATED_NAME, "REGISTERED")))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mockMvc.perform(put("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body(RENAMED, "REGISTERED")));
        mockMvc.perform(delete("/api/users/" + id).header(HttpHeaders.AUTHORIZATION, bearer));

        assertThat(everythingLogged())
                .contains("Usuario creado")
                .contains("Usuario actualizado")
                .contains("Usuario borrado")
                .contains(id);
    }

    @Test
    void rejectedManagementRequestsLogNoPersonalData() throws Exception {
        mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body(CREATED_NAME + "x".repeat(100), "REGISTERED")));
        mockMvc.perform(delete("/api/users/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer));

        assertThat(everythingLogged()).doesNotContain(CREATED_NAME).doesNotContain(ACTOR_NAME);
    }
}
