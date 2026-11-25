package io.github.escumartin.neocatecumenal.identity.infrastructure;

import io.github.escumartin.neocatecumenal.identity.TestCommunityProvisionerConfig;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordHasher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "identity.allowed-origins=https://app.example.test")
@AutoConfigureMockMvc
@Testcontainers
@Import(TestCommunityProvisionerConfig.class)
class LoginLogsTest {

    private static final String NAME = "Zzyzx Quuxington";
    private static final int ACCESS_ID = 424242;
    private static final String PASSWORD = "Sup3r-Secret-Passphrase";
    private static final String WRONG_PASSWORD = "Wr0ng-Secret-Passphrase";
    private static final String IP = "10.9.8.7";
    private static final String COOKIE_NAME = "__Host-session";

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
    private PasswordHasher hasher;

    @Autowired
    private JdbcTemplate jdbc;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger rootLogger;
    private Logger applicationLogger;
    private Level previousApplicationLevel;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
        Community community = communities.save(new Community(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(5)));
        users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(ACCESS_ID), new DisplayName(NAME),
                UserRole.REGISTERED, community.id(), hasher.hash(new PlainPassword(PASSWORD)), false,
                new PrivacyConsent("v1", Instant.now())));
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

    private MockHttpServletRequestBuilder login(String password, String delivery) {
        String body = "{\"accessId\":" + ACCESS_ID + ",\"password\":\"" + password + "\",\"tokenDelivery\":\""
                + delivery + "\"}";
        return post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content(body)
                .header(HttpHeaders.ORIGIN, "https://app.example.test")
                .with(request -> {
                    request.setRemoteAddr(IP);
                    return request;
                });
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
    void successfulFailedAndBlockedLoginsLogNoPersonalDataOrSecrets() throws Exception {
        String body = mockMvc.perform(login(PASSWORD, "BODY")).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(login(WRONG_PASSWORD, "BODY"));
        }
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        mockMvc.perform(delete("/api/sessions/current").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        mockMvc.perform(login(PASSWORD, "COOKIE"));
        mockMvc.perform(get("/api/me").cookie(new Cookie(COOKIE_NAME, "cookie-value-should-not-leak")));

        String logged = everythingLogged();

        assertThat(appender.list).isNotEmpty();
        assertThat(logged)
                .doesNotContain(NAME)
                .doesNotContain(String.valueOf(ACCESS_ID))
                .doesNotContain(PASSWORD)
                .doesNotContain(WRONG_PASSWORD)
                .doesNotContain(token)
                .doesNotContain("cookie-value-should-not-leak")
                .doesNotContain(COOKIE_NAME)
                .doesNotContain(IP)
                .doesNotContain("Bearer");
    }

    @Test
    void theThreeLoginOutcomesAreLogged() throws Exception {
        mockMvc.perform(login(PASSWORD, "BODY"));
        mockMvc.perform(login(WRONG_PASSWORD, "BODY"));
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(WRONG_PASSWORD, "BODY"));
        }

        assertThat(everythingLogged())
                .contains("Inicio de sesión correcto")
                .contains("Inicio de sesión fallido")
                .contains("Inicio de sesión bloqueado");
    }
}
