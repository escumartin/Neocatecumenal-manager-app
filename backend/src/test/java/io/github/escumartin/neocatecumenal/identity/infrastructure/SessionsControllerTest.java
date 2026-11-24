package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
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
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "identity.allowed-origins=https://app.example.test")
@AutoConfigureMockMvc
@Testcontainers
class SessionsControllerTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String ORIGIN = "https://app.example.test";
    private static final String COOKIE_NAME = "__Host-session";

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
    private JdbcTemplate jdbc;

    @Autowired
    private MeterRegistry meters;

    private Community community;
    private User user;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
        community = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(12)));
        user = users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(123456),
                new DisplayName("Test Person"), UserRole.MODERATOR, community.id(),
                hasher.hash(new PlainPassword(PASSWORD)), false, new PrivacyConsent("v1", Instant.now())));
    }

    private static String loginBody(int accessId, String password, String delivery) {
        String deliveryField = delivery == null ? "" : ",\"tokenDelivery\":\"" + delivery + "\"";
        return "{\"accessId\":" + accessId + ",\"password\":\"" + password + "\"" + deliveryField + "}";
    }

    private MockHttpServletRequestBuilder login(String body, String ip) {
        return post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content(body)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    private String loginForToken() throws Exception {
        String body = mockMvc.perform(login(loginBody(123456, PASSWORD, null), "10.0.0.1"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    @Test
    void loginWithBodyDeliveryReturnsTokenAndTheProfile() throws Exception {
        mockMvc.perform(login(loginBody(123456, PASSWORD, "BODY"), "10.0.0.1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.me.userId").value(user.id().value().toString()))
                .andExpect(jsonPath("$.me.accessId").value(123456))
                .andExpect(jsonPath("$.me.name").value("Test Person"))
                .andExpect(jsonPath("$.me.role").value("MODERATOR"))
                .andExpect(jsonPath("$.me.communityId").value(community.id().value().toString()))
                .andExpect(jsonPath("$.me.communityNumber").value(12))
                .andExpect(jsonPath("$.me.pendingSteps", empty()))
                .andExpect(jsonPath("$.me.currentPrivacyNoticeVersion").value("v1"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void loginDefaultsToBodyDelivery() throws Exception {
        mockMvc.perform(login(loginBody(123456, PASSWORD, null), "10.0.0.1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginWithCookieDeliverySetsAHardenedCookieAndOmitsTheTokenFromTheBody() throws Exception {
        mockMvc.perform(login(loginBody(123456, PASSWORD, "COOKIE"), "10.0.0.1").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.me.accessId").value(123456))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(COOKIE_NAME + "=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")));
    }

    @Test
    void loginWithCookieDeliveryDoesNotSerialiseANullToken() throws Exception {
        mockMvc.perform(login(loginBody(123456, PASSWORD, "COOKIE"), "10.0.0.1").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").doesNotHaveJsonPath());
    }

    @Test
    void anAccessIdSentAsAStringOrADecimalIsAValidationProblem() throws Exception {
        mockMvc.perform(login("{\"accessId\":\"123456\",\"password\":\"" + PASSWORD + "\"}", "10.0.0.1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(login("{\"accessId\":123456.0,\"password\":\"" + PASSWORD + "\"}", "10.0.0.1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void aNumericPasswordIsAValidationProblem() throws Exception {
        mockMvc.perform(login("{\"accessId\":123456,\"password\":12345678}", "10.0.0.1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void everyLoginOutcomeIsCountedUnderItsResultTag() throws Exception {
        double success = loginCount("SUCCESS");
        double invalid = loginCount("INVALID_CREDENTIALS");
        double blocked = loginCount("BLOCKED");

        mockMvc.perform(login(loginBody(123456, PASSWORD, null), "10.0.0.1"));
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(loginBody(123456, "wrong-password-value", null), "10.0.0.1"));
        }
        mockMvc.perform(login(loginBody(123456, PASSWORD, null), "10.0.0.1"));

        assertThat(loginCount("SUCCESS") - success).isEqualTo(1.0);
        assertThat(loginCount("INVALID_CREDENTIALS") - invalid).isEqualTo(5.0);
        assertThat(loginCount("BLOCKED") - blocked).isEqualTo(1.0);
    }

    private double loginCount(String result) {
        var counter = meters.find("identity.login").tag("result", result).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void loginWithCookieDeliveryFromAnotherOriginIsForbidden() throws Exception {
        mockMvc.perform(login(loginBody(123456, PASSWORD, "COOKIE"), "10.0.0.1")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.test"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void wrongPasswordAndUnknownAccessIdGetTheSameUnauthorizedProblem() throws Exception {
        String wrong = mockMvc.perform(login(loginBody(123456, "wrong-password-value", null), "10.0.0.1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        String unknown = mockMvc.perform(login(loginBody(654321, "wrong-password-value", null), "10.0.0.1"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknown).isEqualTo(wrong);
    }

    @Test
    void sixthWrongAttemptIsBlockedWithRetryAfter() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login(loginBody(123456, "wrong-password-value", null), "10.0.0.1"))
                    .andExpect(status().isUnauthorized());
        }

        String retryAfter = mockMvc.perform(login(loginBody(123456, PASSWORD, null), "10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("LOGIN_TEMPORARILY_BLOCKED"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);

        assertThat(Integer.parseInt(retryAfter)).isBetween(1, 900);
    }

    @Test
    void failuresFromAnotherIpDoNotBlockTheHolder() throws Exception {
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(login(loginBody(123456, "wrong-password-value", null), "10.0.0.99"));
        }

        mockMvc.perform(login(loginBody(123456, PASSWORD, null), "10.0.0.1")).andExpect(status().isCreated());
    }

    @Test
    void missingAccessIdIsAValidationProblem() throws Exception {
        mockMvc.perform(login("{\"password\":\"" + PASSWORD + "\"}", "10.0.0.1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("accessId"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void accessIdWithoutSixDigitsIsAnInvalidFormatProblem() throws Exception {
        mockMvc.perform(login(loginBody(12345, PASSWORD, null), "10.0.0.1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("accessId"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void emptyPasswordIsARequiredProblem() throws Exception {
        mockMvc.perform(login(loginBody(123456, "", null), "10.0.0.1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void tooLongPasswordIsATooLongProblem() throws Exception {
        mockMvc.perform(login(loginBody(123456, "a".repeat(257), null), "10.0.0.1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
    }

    @Test
    void malformedBodyIsAValidationProblem() throws Exception {
        mockMvc.perform(login("{not json", "10.0.0.1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)));
    }

    @Test
    void visitorOnMeGetsUnauthenticatedProblem() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void unknownBearerTokenIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer unknown"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void meReturnsTheCurrentUserForABearerToken() throws Exception {
        String token = loginForToken();

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.id().value().toString()))
                .andExpect(jsonPath("$.accessId").value(123456))
                .andExpect(jsonPath("$.name").value("Test Person"))
                .andExpect(jsonPath("$.role").value("MODERATOR"))
                .andExpect(jsonPath("$.communityNumber").value(12))
                .andExpect(jsonPath("$.pendingSteps", empty()))
                .andExpect(jsonPath("$.currentPrivacyNoticeVersion").value("v1"))
                .andExpect(jsonPath("$.privacyNoticeAcceptance.version").value("v1"))
                .andExpect(jsonPath("$.privacyNoticeAcceptance.acceptedAt").isNotEmpty());
    }

    @Test
    void meListsPendingStepsOfAFirstAccessUser() throws Exception {
        users.save(User.register(new UserId(UUID.randomUUID()), new AccessId(777777), new DisplayName("New Person"),
                UserRole.REGISTERED, community.id(), hasher.hash(new PlainPassword(PASSWORD))));

        String body = mockMvc.perform(login(loginBody(777777, PASSWORD, null), "10.0.0.2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.me.pendingSteps[0]").value("PRIVACY_NOTICE"))
                .andExpect(jsonPath("$.me.pendingSteps[1]").value("PASSWORD_CHANGE"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingSteps", hasSize(2)))
                .andExpect(jsonPath("$.privacyNoticeAcceptance").doesNotExist());
    }

    @Test
    void meAcceptsTheSessionCookieOnSafeRequestsWithoutOrigin() throws Exception {
        String token = loginForToken();

        mockMvc.perform(get("/api/me").cookie(new Cookie(COOKIE_NAME, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessId").value(123456));
    }

    @Test
    void logOutWithBearerClosesTheSessionSoTheSameTokenIsUnauthorized() throws Exception {
        String token = loginForToken();

        mockMvc.perform(delete("/api/sessions/current").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logOutWithCookieClearsTheCookie() throws Exception {
        String token = loginForToken();

        mockMvc.perform(delete("/api/sessions/current").cookie(new Cookie(COOKIE_NAME, token))
                        .header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(COOKIE_NAME + "=;")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void logOutWithoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/sessions/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void stateChangingRequestWithCookieAndNoOriginIsForbidden() throws Exception {
        String token = loginForToken();

        mockMvc.perform(delete("/api/sessions/current").cookie(new Cookie(COOKIE_NAME, token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/me").cookie(new Cookie(COOKIE_NAME, token))).andExpect(status().isOk());
    }

    @Test
    void stateChangingRequestWithCookieFromAnotherOriginIsForbidden() throws Exception {
        String token = loginForToken();

        mockMvc.perform(delete("/api/sessions/current").cookie(new Cookie(COOKIE_NAME, token))
                        .header(HttpHeaders.ORIGIN, "https://evil.example.test"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/me").cookie(new Cookie(COOKIE_NAME, token))).andExpect(status().isOk());
    }

    @Test
    void stateChangingRequestWithBearerDoesNotNeedAnOrigin() throws Exception {
        String token = loginForToken();

        mockMvc.perform(delete("/api/sessions/current").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.ORIGIN, "https://evil.example.test"))
                .andExpect(status().isNoContent());
    }

    @Test
    void expiredSessionIsUnauthorized() throws Exception {
        String token = "expired-session-token";
        Instant longAgo = Instant.now().minus(Duration.ofDays(31));
        sessions.save(new Session(UUID.randomUUID(), user.id(), tokens.hash(token), longAgo, longAgo));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedUserSessionIsUnauthorized() throws Exception {
        String token = loginForToken();
        jdbc.update("delete from identity_user where id = ?", user.id().value());

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
