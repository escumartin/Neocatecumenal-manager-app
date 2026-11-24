package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PendingStepsIT {

    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(500000);
    private static final String CURRENT_VERSION = "v1";
    private static final String INITIAL = "initial-Passphrase-7";
    private static final String CHOSEN = "Chosen-Passphrase-42";

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

    private Community communityA;
    private Community communityB;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
        communityA = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(1)));
        communityB = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(2)));
    }

    private User user(UserRole role, Community community, boolean passwordChangeRequired, PrivacyConsent consent) {
        return users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(ACCESS_IDS.incrementAndGet()),
                new DisplayName("Test Person"), role, community.id(), hasher.hash(new PlainPassword(INITIAL)),
                passwordChangeRequired, consent));
    }

    private User onboarded(UserRole role, Community community) {
        return user(role, community, false, new PrivacyConsent(CURRENT_VERSION, Instant.now()));
    }

    private User firstAccess(UserRole role, Community community) {
        return user(role, community, true, null);
    }

    private String bearerFor(User user) {
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(user.id(), issued.hash(), Instant.now()));
        return "Bearer " + issued.token();
    }

    private ResultActions getAs(String bearer, String path) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions acceptAs(String bearer, String version) throws Exception {
        return mockMvc.perform(post("/api/me/privacy-notice-acceptance").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"" + version + "\"}"));
    }

    private ResultActions changePasswordAs(String bearer, String current, String next) throws Exception {
        return mockMvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"));
    }

    private ResultActions resetAs(String bearer, User target) throws Exception {
        return mockMvc.perform(post("/api/users/" + target.id().value() + "/password-reset")
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private ResultActions login(User user, String password) throws Exception {
        return mockMvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accessId\":" + user.accessId().value() + ",\"password\":\"" + password + "\"}"));
    }

    @Test
    void withNoticeAndPasswordPendingEveryOtherRouteAnswersPrivacyNoticePending() throws Exception {
        String bearer = bearerFor(firstAccess(UserRole.ADMINISTRATOR, communityA));

        getAs(bearer, "/api/communities")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVACY_NOTICE_PENDING"));
        getAs(bearer, "/api/users?communityId=" + communityA.id().value())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVACY_NOTICE_PENDING"));
        changePasswordAs(bearer, INITIAL, CHOSEN)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVACY_NOTICE_PENDING"));
    }

    @Test
    void theProfileLogoutAndTheNoticeRoutesStayReachableWhileStepsArePending() throws Exception {
        String bearer = bearerFor(firstAccess(UserRole.REGISTERED, communityA));

        getAs(bearer, "/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingSteps", hasSize(2)))
                .andExpect(jsonPath("$.pendingSteps[0]").value("PRIVACY_NOTICE"))
                .andExpect(jsonPath("$.pendingSteps[1]").value("PASSWORD_CHANGE"));
        mockMvc.perform(delete("/api/sessions/current").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());
    }

    @Test
    void afterAcceptingTheNoticeOtherRoutesAnswerPasswordChangeRequired() throws Exception {
        String bearer = bearerFor(firstAccess(UserRole.REGISTERED, communityA));

        acceptAs(bearer, CURRENT_VERSION).andExpect(status().isNoContent());

        getAs(bearer, "/api/communities")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        getAs(bearer, "/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingSteps", hasSize(1)))
                .andExpect(jsonPath("$.pendingSteps[0]").value("PASSWORD_CHANGE"));
    }

    @Test
    void afterAcceptingTheNoticeAndChangingThePasswordTheAccessIsNormal() throws Exception {
        String bearer = bearerFor(firstAccess(UserRole.REGISTERED, communityA));

        acceptAs(bearer, CURRENT_VERSION).andExpect(status().isNoContent());
        changePasswordAs(bearer, INITIAL, CHOSEN).andExpect(status().isNoContent());

        getAs(bearer, "/api/communities").andExpect(status().isOk());
        getAs(bearer, "/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingSteps", hasSize(0)));
    }

    @Test
    void acceptingAnOutdatedNoticeVersionConflictsAndKeepsTheNoticePending() throws Exception {
        String bearer = bearerFor(firstAccess(UserRole.REGISTERED, communityA));

        acceptAs(bearer, "v0")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRIVACY_NOTICE_OUTDATED"));

        getAs(bearer, "/api/communities")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVACY_NOTICE_PENDING"));
    }

    @Test
    void acceptingWithoutAVersionIsAValidationFailure() throws Exception {
        String bearer = bearerFor(firstAccess(UserRole.REGISTERED, communityA));

        mockMvc.perform(post("/api/me/privacy-notice-acceptance").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("version"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void rejectingTheNoticeClosesTheSessionAndTheNoticeIsAskedAgainOnTheNextLogin() throws Exception {
        User user = firstAccess(UserRole.REGISTERED, communityA);
        String bearer = bearerFor(user);

        mockMvc.perform(post("/api/me/privacy-notice-rejection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        getAs(bearer, "/api/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        login(user, INITIAL)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.me.pendingSteps[0]").value("PRIVACY_NOTICE"));
    }

    @Test
    void changingThePasswordClosesTheOtherSessionsKeepsTheCurrentOneAndInvalidatesTheOldPassword() throws Exception {
        User user = onboarded(UserRole.REGISTERED, communityA);
        String current = bearerFor(user);
        String other = bearerFor(user);

        changePasswordAs(current, INITIAL, CHOSEN).andExpect(status().isNoContent());

        getAs(other, "/api/me").andExpect(status().isUnauthorized());
        getAs(current, "/api/me").andExpect(status().isOk());
        login(user, INITIAL).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login(user, CHOSEN).andExpect(status().isCreated());
    }

    @Test
    void theFifthWrongCurrentPasswordInARowClosesEverySessionAndIsUnauthorized() throws Exception {
        User user = onboarded(UserRole.REGISTERED, communityA);
        String current = bearerFor(user);
        String other = bearerFor(user);
        for (int attempt = 0; attempt < 4; attempt++) {
            changePasswordAs(current, "not-the-current-one", CHOSEN).andExpect(status().isUnprocessableEntity());
        }

        changePasswordAs(current, "not-the-current-one", CHOSEN)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        getAs(current, "/api/me").andExpect(status().isUnauthorized());
        getAs(other, "/api/me").andExpect(status().isUnauthorized());
        login(user, INITIAL).andExpect(status().isCreated());
    }

    @Test
    void aCorrectCurrentPasswordBetweenWrongOnesResetsTheCount() throws Exception {
        User user = onboarded(UserRole.REGISTERED, communityA);
        String current = bearerFor(user);
        for (int attempt = 0; attempt < 4; attempt++) {
            changePasswordAs(current, "not-the-current-one", CHOSEN).andExpect(status().isUnprocessableEntity());
        }
        changePasswordAs(current, INITIAL, CHOSEN).andExpect(status().isNoContent());

        for (int attempt = 0; attempt < 4; attempt++) {
            changePasswordAs(current, "not-the-current-one", INITIAL).andExpect(status().isUnprocessableEntity());
        }

        getAs(current, "/api/me").andExpect(status().isOk());
    }

    @Test
    void rejectingTheNoticeAfterAcceptingItShowsItAgainOnTheNextLogin() throws Exception {
        User user = onboarded(UserRole.REGISTERED, communityA);
        String bearer = bearerFor(user);

        mockMvc.perform(post("/api/me/privacy-notice-rejection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        login(user, INITIAL).andExpect(status().isCreated())
                .andExpect(jsonPath("$.me.pendingSteps[0]").value("PRIVACY_NOTICE"))
                .andExpect(jsonPath("$.me.privacyNoticeAcceptance").doesNotExist());
    }

    @Test
    void meDoesNotSerialiseANullPrivacyNoticeAcceptance() throws Exception {
        getAs(bearerFor(firstAccess(UserRole.REGISTERED, communityA)), "/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacyNoticeAcceptance").doesNotHaveJsonPath());
    }

    @Test
    void changingThePasswordWithAWrongCurrentOneIsAFieldErrorAndChangesNothing() throws Exception {
        User user = onboarded(UserRole.REGISTERED, communityA);
        String current = bearerFor(user);
        String other = bearerFor(user);

        changePasswordAs(current, "not-the-current-one", CHOSEN)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"))
                .andExpect(jsonPath("$.errors[0].code").value("CURRENT_PASSWORD_INCORRECT"));

        getAs(other, "/api/me").andExpect(status().isOk());
        login(user, INITIAL).andExpect(status().isCreated());
    }

    @Test
    void theNewPasswordMustSatisfyThePolicy() throws Exception {
        User user = onboarded(UserRole.REGISTERED, communityA);
        String bearer = bearerFor(user);

        changePasswordAs(bearer, INITIAL, "short")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_TOO_SHORT"));
        changePasswordAs(bearer, INITIAL, "a".repeat(65))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_TOO_LONG"));
        changePasswordAs(bearer, INITIAL, "Contraseña123")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_TOO_COMMON"));
        changePasswordAs(bearer, INITIAL, "zz" + user.accessId().value() + "zz")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_CONTAINS_ACCESS_ID"));
    }

    @Test
    void changingThePasswordWithoutASessionIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + INITIAL + "\",\"newPassword\":\"" + CHOSEN + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAdministratorResetClosesEverySessionOfTheTargetAndForcesAPasswordChangeOnTheNextLogin() throws Exception {
        String administrator = bearerFor(onboarded(UserRole.ADMINISTRATOR, communityA));
        User target = onboarded(UserRole.MODERATOR, communityB);
        String first = bearerFor(target);
        String second = bearerFor(target);

        String body = resetAs(administrator, target)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialPassword").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        getAs(first, "/api/me").andExpect(status().isUnauthorized());
        getAs(second, "/api/me").andExpect(status().isUnauthorized());
        getAs(administrator, "/api/me").andExpect(status().isOk());
        String initialPassword = JsonPath.read(body, "$.initialPassword");
        login(target, INITIAL).andExpect(status().isUnauthorized());
        login(target, initialPassword)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.me.pendingSteps", hasSize(1)))
                .andExpect(jsonPath("$.me.pendingSteps[0]").value("PASSWORD_CHANGE"));
    }

    @Test
    void aModeratorResetsARegisteredUserOfTheirCommunityOnly() throws Exception {
        String moderator = bearerFor(onboarded(UserRole.MODERATOR, communityA));
        User own = onboarded(UserRole.REGISTERED, communityA);
        User foreign = onboarded(UserRole.REGISTERED, communityB);
        User fellowModerator = onboarded(UserRole.MODERATOR, communityA);

        resetAs(moderator, own).andExpect(status().isOk());
        resetAs(moderator, foreign).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        resetAs(moderator, fellowModerator).andExpect(status().isForbidden());
        assertThat(users.findById(foreign.id()).orElseThrow().passwordChangeRequired()).isFalse();
    }

    @Test
    void aRegisteredUserCannotResetPasswordsAndAMissingTargetIsNotFound() throws Exception {
        User target = onboarded(UserRole.REGISTERED, communityA);

        resetAs(bearerFor(onboarded(UserRole.REGISTERED, communityA)), target).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users/" + UUID.randomUUID() + "/password-reset")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(onboarded(UserRole.ADMINISTRATOR, communityA))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void nobodyResetsTheirOwnPassword() throws Exception {
        User administrator = onboarded(UserRole.ADMINISTRATOR, communityA);
        String bearer = bearerFor(administrator);

        resetAs(bearer, administrator)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));

        assertThat(users.findById(administrator.id()).orElseThrow().passwordChangeRequired()).isFalse();
        getAs(bearer, "/api/me").andExpect(status().isOk());
    }

    @Test
    void resettingWithoutASessionIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/users/" + UUID.randomUUID() + "/password-reset"))
                .andExpect(status().isUnauthorized());
    }
}
