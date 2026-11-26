package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.bootstrap.BootstrapConsoleProvider;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.bootstrap.BootstrapTermination;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
    "identity.allowed-origins=https://app.example.test",
    "identity.bootstrap.enabled=true",
    "identity.bootstrap.administrator-name=Zzyzx Bootstrapper",
    "identity.bootstrap.administrator-community=1"
})
@AutoConfigureMockMvc
@Testcontainers
@Import(NoPersonalDataInLogsIT.BootstrapConsoleConfig.class)
class NoPersonalDataInLogsIT {

    private static final String ADMINISTRATOR_NAME = "Zzyzx Bootstrapper";
    private static final String CREATED_NAME = "Quuxington Createdson";
    private static final String NEW_PASSWORD = "Brand-New-Secret-Passphrase-3";
    private static final String WRONG_PASSWORD = "Wrong-Secret-Passphrase-4";
    private static final String IP = "10.9.8.7";
    private static final Pattern ACCESS_ID = Pattern.compile("Identificador de acceso: (\\d+)");
    private static final Pattern PASSWORD = Pattern.compile("Contraseña inicial: (\\S+)");
    private static final StringWriter CONSOLE = new StringWriter();
    private static final ListAppender<ILoggingEvent> APPENDER = new ListAppender<>();
    private static final Logger ROOT = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private static final Logger APPLICATION = (Logger) LoggerFactory.getLogger("io.github.escumartin.neocatecumenal");
    private static final Level PREVIOUS_LEVEL;

    static {
        PREVIOUS_LEVEL = APPLICATION.getLevel();
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @TestConfiguration
    static class BootstrapConsoleConfig {

        @Bean
        @Primary
        BootstrapConsoleProvider testConsole() {
            APPLICATION.setLevel(Level.TRACE);
            APPENDER.start();
            ROOT.addAppender(APPENDER);
            return () -> Optional.of(new PrintWriter(CONSOLE, true));
        }

        @Bean
        @Primary
        BootstrapTermination testTermination() {
            return () -> { };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterAll
    static void detach() {
        ROOT.detachAppender(APPENDER);
        APPENDER.stop();
        APPLICATION.setLevel(PREVIOUS_LEVEL);
    }

    private static String find(Pattern pattern) {
        Matcher matcher = pattern.matcher(CONSOLE.toString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private MockHttpServletRequestBuilder login(String accessId, String password) {
        String body = "{\"accessId\":" + accessId + ",\"password\":\"" + password + "\",\"tokenDelivery\":\"BODY\"}";
        return post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content(body)
                .header(HttpHeaders.ORIGIN, "https://app.example.test")
                .with(request -> {
                    request.setRemoteAddr(IP);
                    return request;
                });
    }

    private String tokenOf(String accessId, String password) throws Exception {
        String body = mockMvc.perform(login(accessId, password)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String everythingLogged() {
        List<String> lines = new ArrayList<>();
        for (ILoggingEvent event : new ArrayList<>(APPENDER.list)) {
            lines.add(event.getFormattedMessage());
            lines.add(String.valueOf(event.getKeyValuePairs()));
            lines.add(String.valueOf(event.getArgumentArray() == null ? "" : List.of(event.getArgumentArray())));
            lines.add(String.valueOf(event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage()));
        }
        return String.join("\n", lines);
    }

    @Test
    void bootstrapLoginCreationPasswordChangeAndResetLogNoPersonalDataOrCredentials() throws Exception {
        String administratorAccessId = find(ACCESS_ID);
        String administratorPassword = find(PASSWORD);
        String communityId = jdbc.queryForObject("select id::text from identity_community limit 1", String.class);

        String restricted = tokenOf(administratorAccessId, administratorPassword);
        mockMvc.perform(login(administratorAccessId, WRONG_PASSWORD));
        mockMvc.perform(post("/api/me/privacy-notice-acceptance").header(HttpHeaders.AUTHORIZATION, bearer(restricted))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"v1\"}"));
        mockMvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, bearer(restricted))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + administratorPassword + "\",\"newPassword\":\"" + NEW_PASSWORD
                        + "\"}"));
        String token = tokenOf(administratorAccessId, NEW_PASSWORD);
        String created = mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + CREATED_NAME + "\",\"role\":\"REGISTERED\",\"communityId\":\""
                                + communityId + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String createdId = JsonPath.read(created, "$.id");
        String createdAccessId = String.valueOf((Integer) JsonPath.read(created, "$.accessId"));
        String createdPassword = JsonPath.read(created, "$.initialPassword");
        String reset = mockMvc.perform(post("/api/users/" + createdId + "/password-reset")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getContentAsString();
        String resetPassword = JsonPath.read(reset, "$.initialPassword");

        assertThat(APPENDER.list).isNotEmpty();
        assertThat(createdPassword).isNotBlank();
        assertThat(resetPassword).isNotBlank();
        assertThat(everythingLogged())
                .contains("Carga inicial completada")
                .doesNotContain(ADMINISTRATOR_NAME)
                .doesNotContain(CREATED_NAME)
                .doesNotContain(administratorAccessId)
                .doesNotContain(createdAccessId)
                .doesNotContain(administratorPassword)
                .doesNotContain(NEW_PASSWORD)
                .doesNotContain(WRONG_PASSWORD)
                .doesNotContain(createdPassword)
                .doesNotContain(resetPassword)
                .doesNotContain(restricted)
                .doesNotContain(token)
                .doesNotContain(IP)
                .doesNotContain("Bearer");
    }
}
