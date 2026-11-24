package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ListCommunities;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ErrorResponsesIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @LocalServerPort
    private int port;

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

    @MockitoBean
    private ListCommunities listCommunities;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger rootLogger;
    private Community community;
    private String bearer;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
        community = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(1)));
        User administrator = users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(777001),
                new DisplayName("Test Person"), UserRole.ADMINISTRATOR, community.id(),
                new PasswordHash("{argon2}hash"), false, new PrivacyConsent("v1", Instant.now())));
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(administrator.id(), issued.hash(), Instant.now()));
        bearer = "Bearer " + issued.token();
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    private HttpResponse<String> send(String method, String path, String contentType, String body, boolean signedIn)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        if (signedIn) {
            request.header("Authorization", bearer);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat((Integer) JsonPath.read(response.body(), "$.status")).isEqualTo(status);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo(code);
    }

    private List<ILoggingEvent> logged() {
        return new ArrayList<>(appender.list);
    }

    @Test
    void anUnsupportedMethodIsAMethodNotAllowedProblem() throws Exception {
        assertProblem(send("PATCH", "/api/users", null, null, true), 405, "METHOD_NOT_ALLOWED");
    }

    @Test
    void anUnsupportedMediaTypeIsAnUnsupportedMediaTypeProblem() throws Exception {
        assertProblem(send("POST", "/api/users", "text/plain", "name", true), 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void aPathWithANulByteIsABadRequestProblem() throws Exception {
        assertProblem(send("GET", "/api/users/%00", null, null, true), 400, "BAD_REQUEST");
    }

    @Test
    void aNonUuidUserIdIsAValidationProblem() throws Exception {
        assertProblem(send("GET", "/api/users/not-a-uuid", null, null, true), 422, "VALIDATION_FAILED");
    }

    @Test
    void anUnknownRouteIsStillNotFoundAndNoSessionIsStillUnauthorized() throws Exception {
        assertProblem(send("GET", "/api/nothing-here", null, null, true), 404, "NOT_FOUND");
        assertProblem(send("GET", "/api/me", null, null, false), 401, "UNAUTHENTICATED");
    }

    @Test
    void aPageBeyondTheRepresentableRangeIsAnEmptyPage() throws Exception {
        HttpResponse<String> response = send("GET",
                "/api/users?communityId=" + community.id().value() + "&page=2147483647&size=100", null, null, true);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((List<?>) JsonPath.read(response.body(), "$.items")).isEmpty();
        assertThat((Integer) JsonPath.read(response.body(), "$.totalItems")).isEqualTo(1);
    }

    @Test
    void anUnexpectedFailureIsAnInternalErrorProblemWithoutLeakingItsMessageAndIsLoggedWithItsCause()
            throws Exception {
        when(listCommunities.list()).thenThrow(new IllegalStateException("leaky-internal-detail"));

        HttpResponse<String> response = send("GET", "/api/communities", null, null, true);

        assertProblem(response, 500, "INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain("leaky-internal-detail");
        assertThat(logged()).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy().getMessage()).isEqualTo("leaky-internal-detail");
        });
    }

    @Test
    void problemResponsesAreLoggedAsWarningsWithCodeAndStatusOnly() throws Exception {
        send("GET", "/api/users/not-a-uuid", null, null, true);
        send("GET", "/api/me", null, null, false);

        assertThat(logged()).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key + "=" + pair.value)
                    .contains("code=VALIDATION_FAILED", "status=422");
        });
        assertThat(logged()).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key + "=" + pair.value)
                    .contains("code=UNAUTHENTICATED", "status=401");
        });
        assertThat(logged()).noneMatch(event -> event.getFormattedMessage().contains("not-a-uuid"));
    }
}
