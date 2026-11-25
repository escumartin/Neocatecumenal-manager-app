package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class MyMemberControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

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

    private CommunityApiFixture fixture;
    private UUID home;
    private UUID member;
    private UUID ownUser;
    private String own;
    private String moderator;
    private String unlinked;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        home = fixture.community(1);
        member = fixture.member(home, "Ana");
        ownUser = fixture.user(UserRole.REGISTERED, home);
        jdbc.update("update community_member set user_id = ? where id = ?", ownUser, member);
        own = fixture.bearerOf(ownUser);
        moderator = fixture.bearer(UserRole.MODERATOR, home);
        unlinked = fixture.bearer(UserRole.REGISTERED, home);
    }

    private MockHttpServletRequestBuilder asJson(MockHttpServletRequestBuilder builder, String bearer, String body) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String bearer) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer);
    }

    private static String field(boolean shared, String value) {
        return value == null ? "{\"shared\":" + shared + "}"
                : "{\"shared\":" + shared + ",\"value\":\"" + value + "\"}";
    }

    private static String contact(String mobile, String landline, String email) {
        return "{\"mobile\":" + mobile + ",\"landline\":" + landline + ",\"email\":" + email + "}";
    }

    private static String mobileOnly(String value) {
        return contact(field(true, value), field(false, null), field(false, null));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void getReturnsTheLinkedMemberWithNoConsentsYet() throws Exception {
        mockMvc.perform(as(get("/api/me/member"), own))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.id").value(member.toString()))
                .andExpect(jsonPath("$.member.givenName").value("Ana"))
                .andExpect(jsonPath("$.currentConsents", hasSize(0)));
    }

    @Test
    void getWithoutALinkedMemberAnswers409MemberNotLinked() throws Exception {
        mockMvc.perform(as(get("/api/me/member"), unlinked))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_LINKED"));
    }

    @Test
    void getWithoutSessionAnswers401() throws Exception {
        mockMvc.perform(get("/api/me/member")).andExpect(status().isUnauthorized());
    }

    @Test
    void putSharesTheMobileAndRecordsAnAppConsentOfTheMember() throws Exception {
        mockMvc.perform(asJson(put("/api/me/member/contact"), own, mobileOnly("600 000 001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.mobile").value("600000001"))
                .andExpect(jsonPath("$.currentConsents", hasSize(1)))
                .andExpect(jsonPath("$.currentConsents[0].field").value("MOBILE"))
                .andExpect(jsonPath("$.currentConsents[0].decision").value("GRANTED"))
                .andExpect(jsonPath("$.currentConsents[0].channel").value("APP"))
                .andExpect(jsonPath("$.currentConsents[0].recordedBy.role").value("MEMBER"))
                .andExpect(jsonPath("$.currentConsents[0].recordedBy.deleted").value(false))
                .andExpect(jsonPath("$.currentConsents[0].recordedBy.name").doesNotExist());

        assertThat(jdbc.queryForObject("select mobile from community_member where id = ?", String.class, member))
                .isEqualTo("600000001");
        assertThat(jdbc.queryForMap("select channel, recorder_role, recorded_by_user_id "
                + "from community_contact_consent where member_id = ?", member))
                .containsEntry("channel", "APP").containsEntry("recorder_role", "MEMBER")
                .containsEntry("recorded_by_user_id", ownUser);
    }

    @Test
    void putWithAnInvalidPhoneAnswers422AndKeepsThePreviousValues() throws Exception {
        mockMvc.perform(asJson(put("/api/me/member/contact"), own, mobileOnly("600000001")))
                .andExpect(status().isOk());

        mockMvc.perform(asJson(put("/api/me/member/contact"), own, mobileOnly("512345678")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("mobile"))
                .andExpect(jsonPath("$.errors[0].code").value("PHONE_INVALID"));

        assertThat(jdbc.queryForObject("select mobile from community_member where id = ?", String.class, member))
                .isEqualTo("600000001");
        assertThat(count("community_contact_consent")).isEqualTo(1);
    }

    @Test
    void putWithSharedFalseErasesTheValueAndRecordsTheWithdrawal() throws Exception {
        mockMvc.perform(asJson(put("/api/me/member/contact"), own, mobileOnly("600000001")))
                .andExpect(status().isOk());

        mockMvc.perform(asJson(put("/api/me/member/contact"), own,
                contact(field(false, null), field(false, null), field(false, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.mobile").doesNotExist())
                .andExpect(jsonPath("$.currentConsents[0].decision").value("WITHDRAWN"));

        assertThat(jdbc.queryForObject("select mobile from community_member where id = ?", String.class, member))
                .isNull();
        assertThat(count("community_contact_consent")).isEqualTo(2);
    }

    @Test
    void putWithoutALinkedMemberAnswers409MemberNotLinked() throws Exception {
        mockMvc.perform(asJson(put("/api/me/member/contact"), unlinked, mobileOnly("600000001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_LINKED"));
    }

    @Test
    void aRegisteredUserCannotChangeAnotherMembersContact() throws Exception {
        UUID other = fixture.member(home, "Luis");

        mockMvc.perform(asJson(put("/api/members/" + other + "/contact"), own, mobileOnly("600000001")))
                .andExpect(status().isForbidden());

        assertThat(count("community_contact_consent")).isZero();
    }

    @Test
    void theBodyDoesNotAcceptNameNorCommunity() throws Exception {
        UUID away = fixture.community(2);
        String body = "{\"givenName\":\"Otro\",\"communityId\":\"" + away + "\",\"mobile\":" + field(true, "600000001")
                + ",\"landline\":" + field(false, null) + ",\"email\":" + field(false, null) + "}";

        mockMvc.perform(asJson(put("/api/me/member/contact"), own, body)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select given_name from community_member where id = ?", String.class, member))
                .isEqualTo("Ana");
        assertThat(jdbc.queryForObject("select community_id from community_member where id = ?", UUID.class, member))
                .isEqualTo(home);
    }

    @Test
    void currentConsentsShowTheVerbalChannelAndRecorderNameThenTheOwnDecisionAppears() throws Exception {
        UUID moderatorUser = jdbc.queryForObject("select id from identity_user where role = 'MODERATOR'", UUID.class);
        jdbc.update("update community_member set mobile = '600000001' where id = ?", member);
        jdbc.update("insert into community_contact_consent (id, member_id, field, decision, channel, "
                + "recorded_by_user_id, recorder_role, notice_given, recorded_at) "
                + "values (?, ?, 'MOBILE', 'GRANTED', 'VERBAL', ?, 'MODERATOR', true, now() - interval '1 day')",
                UUID.randomUUID(), member, moderatorUser);

        mockMvc.perform(as(get("/api/me/member"), own))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentConsents[0].channel").value("VERBAL"))
                .andExpect(jsonPath("$.currentConsents[0].recordedBy.role").value("MODERATOR"))
                .andExpect(jsonPath("$.currentConsents[0].recordedBy.name").value("Test Person"))
                .andExpect(jsonPath("$.currentConsents[0].recordedBy.deleted").value(false));

        mockMvc.perform(asJson(put("/api/me/member/contact"), own,
                contact(field(false, null), field(false, null), field(false, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentConsents[0].channel").value("APP"))
                .andExpect(jsonPath("$.currentConsents[0].decision").value("WITHDRAWN"));
    }

    @Test
    void aModeratorCannotChangeWhichFieldsAreSharedForALinkedMember() throws Exception {
        mockMvc.perform(asJson(put("/api/members/" + member + "/contact"), moderator,
                mobileOnly("600000001").replace("}}", "}," + "\"onBehalfConsent\":"
                        + "{\"channel\":\"VERBAL\",\"noticeGiven\":true}}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_MANAGED_BY_MEMBER"));
    }
}
