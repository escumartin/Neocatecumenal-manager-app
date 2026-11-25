package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
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
class MemberLogsTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final List<String> PERSONAL_DATA = List.of("Zenobia", "Quirógrafo", "677123456", "677123457",
            "+34988765432", "zenobia.q@example.test");
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
    private UUID home;
    private String moderator;

    @BeforeEach
    void setUp() {
        CommunityApiFixture fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        home = fixture.community(1);
        moderator = fixture.bearer(UserRole.MODERATOR, home);
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    private MockHttpServletRequestBuilder asJson(MockHttpServletRequestBuilder builder, String body) {
        return builder.header(HttpHeaders.AUTHORIZATION, moderator).contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String contact(String mobile, String landline, String email) {
        return "{\"mobile\":{\"shared\":" + (mobile != null) + (mobile != null ? ",\"value\":\"" + mobile + "\"" : "")
                + "},\"landline\":{\"shared\":" + (landline != null)
                + (landline != null ? ",\"value\":\"" + landline + "\"" : "") + "},\"email\":{\"shared\":"
                + (email != null) + (email != null ? ",\"value\":\"" + email + "\"" : "") + "}}";
    }

    private String everythingLogged() {
        return appender.list.stream().map(event -> event.getFormattedMessage() + " " + event.getKeyValuePairs()
                + " " + event.getMDCPropertyMap() + " " + event.getArgumentArray()
                + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy())))
                .collect(Collectors.joining("\n"));
    }

    private void assertNoPersonalDataLogged() {
        assertThat(everythingLogged()).doesNotContain(PERSONAL_DATA);
    }

    @Test
    void creatingAndChangingContactLogsNoPersonalDataButStillLogsTheEvents() throws Exception {
        String created = mockMvc.perform(asJson(post("/api/members"), "{\"communityId\":\"" + home
                + "\",\"givenName\":\"Zenobia\",\"surnames\":\"Quirógrafo\",\"contact\":"
                + contact("677123456", "+34988765432", "zenobia.q@example.test") + "," + VERBAL + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        mockMvc.perform(asJson(put("/api/members/" + id + "/contact"),
                contact("677123457", "+34988765432", "zenobia.q@example.test")))
                .andExpect(status().isOk());
        mockMvc.perform(asJson(put("/api/members/" + id), "{\"givenName\":\"Zenobia\",\"surnames\":\"Quirógrafo\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/members/" + id).header(HttpHeaders.AUTHORIZATION, moderator))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/members").header(HttpHeaders.AUTHORIZATION, moderator))
                .andExpect(status().isOk());

        assertThat(everythingLogged()).contains(id);
        assertNoPersonalDataLogged();
    }

    @Test
    void validationAndAuthorizationErrorsLogNoPersonalData() throws Exception {
        mockMvc.perform(asJson(post("/api/members"), "{\"communityId\":\"" + home
                + "\",\"givenName\":\"Zenobia\",\"surnames\":\"Quirógrafo\",\"contact\":"
                + contact("512345678", null, null) + "," + VERBAL + "}")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(asJson(post("/api/members"), "{\"communityId\":\"" + home
                + "\",\"givenName\":\"Zenobia\",\"contact\":" + contact(null, null, "a@b") + "," + VERBAL + "}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(asJson(post("/api/members"), "{\"communityId\":\"" + home
                + "\",\"givenName\":\"Zenobia\",\"contact\":" + contact("677123456", null, null) + "}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(asJson(post("/api/members"), "{\"communityId\":\"" + UUID.randomUUID()
                + "\",\"givenName\":\"Zenobia\",\"surnames\":\"Quirógrafo\"}")).andExpect(status().isForbidden());
        mockMvc.perform(asJson(post("/api/members"), "{\"givenName\":\"Zenobia\","))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(asJson(put("/api/members/" + UUID.randomUUID()),
                "{\"givenName\":\"Zenobia\",\"surnames\":\"Quirógrafo\"}")).andExpect(status().isNotFound());

        assertThat(appender.list).isNotEmpty();
        assertNoPersonalDataLogged();
    }
}
