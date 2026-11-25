package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class NoPersonalDataInLogsIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final List<String> PERSONAL_DATA = List.of("Zenobia", "Quirógrafo", "Hermenegilda", "Valdemoro",
            "600000101", "600000102", "600000103", "+34600000201", "+34600000202", "zenobia.q@example.test",
            "hermenegilda.v@example.test");
    private static final String VERBAL = "\"onBehalfConsent\":{\"channel\":\"VERBAL\",\"noticeGiven\":true}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private SessionTokenIssuer tokens;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger rootLogger;
    private CommunityApiFixture fixture;
    private UUID home;
    private UUID away;
    private String administrator;
    private String moderator;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        home = fixture.community(1);
        away = fixture.community(2);
        moderator = fixture.bearer(UserRole.MODERATOR, home);
        administrator = fixture.bearer(UserRole.ADMINISTRATOR, home);
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String bearer, String body) {
        MockHttpServletRequestBuilder authorized = builder.header(HttpHeaders.AUTHORIZATION, bearer);
        return body == null ? authorized : authorized.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String field(String name, String value) {
        return "\"" + name + "\":{\"shared\":" + (value != null)
                + (value != null ? ",\"value\":\"" + value + "\"" : "") + "}";
    }

    private static String contact(String mobile, String landline, String email) {
        return "{" + field("mobile", mobile) + "," + field("landline", landline) + "," + field("email", email) + "}";
    }

    private static String contactWithConsent(String mobile, String landline, String email) {
        String body = contact(mobile, landline, email);
        return body.substring(0, body.length() - 1) + "," + VERBAL + "}";
    }

    private String everythingLogged() {
        return appender.list.stream().map(event -> event.getFormattedMessage() + " " + event.getKeyValuePairs()
                + " " + event.getMDCPropertyMap() + " " + event.getArgumentArray()
                + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy())))
                .collect(Collectors.joining("\n"));
    }

    @Test
    void theWholeMemberLifecycleLogsNoPersonalData() throws Exception {
        String created = mockMvc.perform(as(post("/api/members"), moderator, "{\"communityId\":\"" + home
                + "\",\"givenName\":\"Zenobia\",\"surnames\":\"Quirógrafo\",\"contact\":"
                + contact("600000101", "+34600000201", "zenobia.q@example.test") + "," + VERBAL + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mockMvc.perform(as(put("/api/members/" + id + "/contact"), moderator,
                contactWithConsent(null, null, "zenobia.q@example.test"))).andExpect(status().isOk());
        mockMvc.perform(as(put("/api/members/" + id + "/contact"), moderator,
                contactWithConsent("600000102", "+34600000202", "zenobia.q@example.test")))
                .andExpect(status().isOk());
        mockMvc.perform(as(put("/api/members/" + id),
                moderator, "{\"givenName\":\"Hermenegilda\",\"surnames\":\"Valdemoro\"}")).andExpect(status().isOk());

        UUID userId = fixture.user(UserRole.REGISTERED, home);
        mockMvc.perform(as(put("/api/members/" + id + "/link"), moderator, "{\"userId\":\"" + userId + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(put("/api/me/member/contact"), fixture.bearerOf(userId),
                contact("600000103", null, "hermenegilda.v@example.test"))).andExpect(status().isOk());
        mockMvc.perform(as(get("/api/me/member"), fixture.bearerOf(userId), null)).andExpect(status().isOk());
        mockMvc.perform(as(get("/api/members/" + id + "/data-export"), moderator, null)).andExpect(status().isOk());
        mockMvc.perform(as(get("/api/members"), moderator, null)).andExpect(status().isOk());

        UUID other = fixture.member(home, "Ana");
        mockMvc.perform(as(post("/api/members/" + id + "/transfer"), administrator,
                "{\"communityId\":\"" + away + "\"}")).andExpect(status().isOk());
        mockMvc.perform(as(put("/api/communities/" + away + "/responsible"), administrator,
                "{\"memberId\":\"" + id + "\"}")).andExpect(status().isOk());
        mockMvc.perform(as(post("/api/members/" + id + "/transfer"), administrator,
                "{\"communityId\":\"" + home + "\"}")).andExpect(status().isConflict());
        fixture.designateResponsible(away, fixture.member(away, "Luis"));
        mockMvc.perform(as(delete("/api/members/" + other), moderator, null)).andExpect(status().isNoContent());
        mockMvc.perform(as(delete("/api/members/" + id + "/link"), administrator, null))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(get("/api/members/" + id + "/data-export"), administrator, null))
                .andExpect(status().isOk());
        mockMvc.perform(as(delete("/api/members/" + id), administrator, null)).andExpect(status().isNoContent());

        assertThat(appender.list).isNotEmpty();
        assertThat(everythingLogged()).contains(id).doesNotContain(PERSONAL_DATA);
    }

    @Test
    void failedOperationsLogNoPersonalData() throws Exception {
        UUID foreignMember = fixture.member(away, "Zenobia");
        jdbc.update("update community_member set surnames = 'Quirógrafo', mobile = '600000101' where id = ?",
                foreignMember);

        mockMvc.perform(as(get("/api/members/" + foreignMember + "/data-export"), moderator, null))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(put("/api/members/" + foreignMember + "/link"), moderator,
                "{\"userId\":\"" + fixture.user(UserRole.REGISTERED, away) + "\"}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(as(delete("/api/members/" + foreignMember), moderator, null))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(post("/api/members/" + foreignMember + "/transfer"), moderator,
                "{\"communityId\":\"" + home + "\"}")).andExpect(status().isForbidden());
        mockMvc.perform(as(put("/api/members/" + foreignMember + "/contact"), moderator,
                contactWithConsent("600000101", null, "zenobia.q@example.test"))).andExpect(status().isNotFound());
        mockMvc.perform(as(put("/api/members/" + UUID.randomUUID() + "/contact"), moderator,
                contactWithConsent("zenobia.q@example.test", null, null))).andExpect(status().isNotFound());

        assertThat(appender.list).isNotEmpty();
        assertThat(everythingLogged()).doesNotContain(PERSONAL_DATA);
    }
}
