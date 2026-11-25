package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class MembersControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

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

    private CommunityApiFixture fixture;
    private UUID home;
    private UUID away;
    private String administrator;
    private String moderator;
    private String registered;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        home = fixture.community(1);
        away = fixture.community(2);
        administrator = fixture.bearer(UserRole.ADMINISTRATOR, home);
        moderator = fixture.bearer(UserRole.MODERATOR, home);
        registered = fixture.bearer(UserRole.REGISTERED, home);
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

    private static String memberBody(UUID community, String given, String surnames, String contact, String consent) {
        StringBuilder body = new StringBuilder("{\"communityId\":\"" + community + "\",\"givenName\":\"" + given
                + "\"");
        if (surnames != null) {
            body.append(",\"surnames\":\"").append(surnames).append("\"");
        }
        if (contact != null) {
            body.append(",\"contact\":").append(contact);
        }
        if (consent != null) {
            body.append(",").append(consent);
        }
        return body.append("}").toString();
    }

    private static String mobileOnly(String value) {
        return contact(field(true, value), field(false, null), field(false, null));
    }

    private String createMember(String bearer, String body) throws Exception {
        String response = mockMvc.perform(asJson(post("/api/members"), bearer, body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void createReturns201WithLocationAndStoresTheNormalizedMobileWithItsConsent() throws Exception {
        var response = mockMvc.perform(asJson(post("/api/members"), moderator,
                memberBody(home, "Ana", "Pérez", mobileOnly("600 000 001"), VERBAL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.givenName").value("Ana"))
                .andExpect(jsonPath("$.surnames").value("Pérez"))
                .andExpect(jsonPath("$.communityId").value(home.toString()))
                .andExpect(jsonPath("$.mobile").value("600000001"))
                .andExpect(jsonPath("$.landline").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.isResponsible").value(false))
                .andExpect(jsonPath("$.linkedUserId").doesNotExist())
                .andReturn().getResponse();

        String id = JsonPath.read(response.getContentAsString(), "$.id");
        assertThat(response.getHeader("Location")).endsWith("/api/members/" + id);
        assertThat(jdbc.queryForObject("select mobile from community_member where id = ?::uuid", String.class, id))
                .isEqualTo("600000001");
        assertThat(jdbc.queryForMap("select field, decision, channel, recorder_role, notice_given "
                + "from community_contact_consent where member_id = ?::uuid", id))
                .containsEntry("field", "MOBILE").containsEntry("decision", "GRANTED")
                .containsEntry("channel", "VERBAL").containsEntry("recorder_role", "MODERATOR")
                .containsEntry("notice_given", true);
    }

    @Test
    void createWithoutSurnamesAndWithoutContactReturns201() throws Exception {
        mockMvc.perform(asJson(post("/api/members"), moderator, memberBody(home, "Ana", null, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.surnames").doesNotExist());

        assertThat(count("community_contact_consent")).isZero();
    }

    @Test
    void createWithoutAGivenNameFailsWithRequired() throws Exception {
        mockMvc.perform(asJson(post("/api/members"), moderator, "{\"communityId\":\"" + home + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("givenName"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));

        assertThat(count("community_member")).isZero();
    }

    @Test
    void createWithABlankGivenNameFailsWithRequired() throws Exception {
        mockMvc.perform(asJson(post("/api/members"), moderator, memberBody(home, "   ", null, null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("givenName"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void createWithAnInvalidPhoneFailsAndStoresNothing() throws Exception {
        mockMvc.perform(asJson(post("/api/members"), moderator,
                memberBody(home, "Ana", null, mobileOnly("512345678"), VERBAL)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("mobile"))
                .andExpect(jsonPath("$.errors[0].code").value("PHONE_INVALID"));

        assertThat(count("community_member")).isZero();
        assertThat(count("community_contact_consent")).isZero();
    }

    @Test
    void createWithAnInvalidEmailFailsAndStoresNothing() throws Exception {
        String bad = contact(field(false, null), field(false, null), field(true, "a@b"));

        mockMvc.perform(asJson(post("/api/members"), moderator, memberBody(home, "Ana", null, bad, VERBAL)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("EMAIL_INVALID"));

        assertThat(count("community_member")).isZero();
    }

    @Test
    void createWithASharedFieldButNoValueFailsWithRequired() throws Exception {
        String missing = contact(field(true, null), field(false, null), field(false, null));

        mockMvc.perform(asJson(post("/api/members"), moderator, memberBody(home, "Ana", null, missing, VERBAL)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("mobile"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void createWithContactButNoConsentFailsWithConsentRequiredAndStoresNothing() throws Exception {
        mockMvc.perform(asJson(post("/api/members"), moderator,
                memberBody(home, "Ana", null, mobileOnly("600000001"), null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("CONSENT_REQUIRED"));

        assertThat(count("community_member")).isZero();
    }

    @Test
    void createWithoutTheNoticeFailsWithNoticeNotGivenAndStoresNothing() throws Exception {
        String withoutNotice = "\"onBehalfConsent\":{\"channel\":\"WRITTEN\",\"noticeGiven\":false}";

        mockMvc.perform(asJson(post("/api/members"), moderator,
                memberBody(home, "Ana", null, mobileOnly("600000001"), withoutNotice)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("NOTICE_NOT_GIVEN"));

        assertThat(count("community_member")).isZero();
        assertThat(count("community_contact_consent")).isZero();
    }

    @Test
    void aModeratorCannotCreateInAnotherCommunity() throws Exception {
        mockMvc.perform(asJson(post("/api/members"), moderator, memberBody(away, "Ana", null, null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(count("community_member")).isZero();
    }

    @Test
    void anAdministratorCreatesInAnyCommunity() throws Exception {
        mockMvc.perform(asJson(post("/api/members"), administrator, memberBody(away, "Ana", null, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.communityId").value(away.toString()));
    }

    @Test
    void aRegisteredUserCannotCreate() throws Exception {
        mockMvc.perform(asJson(post("/api/members"), registered, memberBody(home, "Ana", null, null, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/members").contentType(MediaType.APPLICATION_JSON)
                .content(memberBody(home, "Ana", null, null, null))).andExpect(status().isUnauthorized());
    }

    @Test
    void createInADeactivatedCommunityFailsOnTheCommunityField() throws Exception {
        jdbc.update("update community set status = 'DEACTIVATED' where id = ?", away);

        mockMvc.perform(asJson(post("/api/members"), administrator, memberBody(away, "Ana", null, null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("communityId"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_DEACTIVATED"));
    }

    @Test
    void createInAnUnknownCommunityFailsOnTheCommunityField() throws Exception {
        mockMvc.perform(asJson(post("/api/members"), administrator,
                memberBody(UUID.randomUUID(), "Ana", null, null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("communityId"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_NOT_FOUND"));
    }

    @Test
    void changeContactReplacesTheValuesAndRecordsOnlyTheChangedConsents() throws Exception {
        String id = createMember(moderator, memberBody(home, "Ana", "Pérez", mobileOnly("600000001"), VERBAL));
        String body = "{\"mobile\":" + field(true, "600 000 002") + ",\"landline\":" + field(true, "912-345-678")
                + ",\"email\":" + field(false, null) + ",\"onBehalfConsent\":{\"channel\":\"WRITTEN\","
                + "\"noticeGiven\":true}}";

        mockMvc.perform(asJson(put("/api/members/" + id + "/contact"), moderator, body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobile").value("600000002"))
                .andExpect(jsonPath("$.landline").value("912345678"))
                .andExpect(jsonPath("$.email").doesNotExist());

        assertThat(jdbc.queryForList("select field from community_contact_consent where member_id = ?::uuid "
                + "order by recorded_at, field", String.class, id)).containsExactlyInAnyOrder("MOBILE", "LANDLINE");
    }

    @Test
    void changeContactWithdrawingAFieldErasesItsValue() throws Exception {
        String id = createMember(moderator, memberBody(home, "Ana", "Pérez", mobileOnly("600000001"), VERBAL));
        String body = "{\"mobile\":" + field(false, null) + ",\"landline\":" + field(false, null) + ",\"email\":"
                + field(false, null) + "," + VERBAL + "}";

        mockMvc.perform(asJson(put("/api/members/" + id + "/contact"), moderator, body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobile").doesNotExist());

        assertThat(jdbc.queryForObject("select mobile from community_member where id = ?::uuid", String.class, id))
                .isNull();
        assertThat(jdbc.queryForList("select decision from community_contact_consent where member_id = ?::uuid",
                String.class, id)).containsExactlyInAnyOrder("GRANTED", "WITHDRAWN");
    }

    @Test
    void changeContactRectifyingASharedValueNeedsNoConsentContextAndAddsNoRecord() throws Exception {
        String id = createMember(moderator, memberBody(home, "Ana", "Pérez", mobileOnly("600000001"), VERBAL));
        String body = "{\"mobile\":" + field(true, "600000002") + ",\"landline\":" + field(false, null)
                + ",\"email\":" + field(false, null) + "}";

        mockMvc.perform(asJson(put("/api/members/" + id + "/contact"), moderator, body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobile").value("600000002"));

        assertThat(count("community_contact_consent")).isEqualTo(1);
    }

    @Test
    void changeContactSharingANewFieldWithoutConsentContextFails() throws Exception {
        String id = createMember(moderator, memberBody(home, "Ana", "Pérez", null, null));
        String body = contact(field(true, "600000001"), field(false, null), field(false, null));

        mockMvc.perform(asJson(put("/api/members/" + id + "/contact"), moderator, body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("CONSENT_REQUIRED"));

        assertThat(jdbc.queryForObject("select mobile from community_member where id = ?::uuid", String.class, id))
                .isNull();
    }

    @Test
    void changeContactWithoutTheNoticeFails() throws Exception {
        String id = createMember(moderator, memberBody(home, "Ana", "Pérez", null, null));
        String body = "{\"mobile\":" + field(true, "600000001") + ",\"landline\":" + field(false, null)
                + ",\"email\":" + field(false, null)
                + ",\"onBehalfConsent\":{\"channel\":\"VERBAL\",\"noticeGiven\":false}}";

        mockMvc.perform(asJson(put("/api/members/" + id + "/contact"), moderator, body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("NOTICE_NOT_GIVEN"));
    }

    @Test
    void changeContactOfAMemberOfAnotherCommunityAnswers404() throws Exception {
        UUID foreign = fixture.member(away, "Luis");
        String body = "{\"mobile\":" + field(true, "600000001") + ",\"landline\":" + field(false, null)
                + ",\"email\":" + field(false, null) + "," + VERBAL + "}";

        mockMvc.perform(asJson(put("/api/members/" + foreign + "/contact"), moderator, body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void renameReplacesTheNameAndAnAbsentSurnamesClearsThem() throws Exception {
        String id = createMember(moderator, memberBody(home, "Ana", "Pérez", null, null));

        mockMvc.perform(asJson(put("/api/members/" + id), moderator, "{\"givenName\":\"Beatriz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.givenName").value("Beatriz"))
                .andExpect(jsonPath("$.surnames").doesNotExist());
    }

    @Test
    void renameWithoutAGivenNameFailsWithRequired() throws Exception {
        String id = createMember(moderator, memberBody(home, "Ana", "Pérez", null, null));

        mockMvc.perform(asJson(put("/api/members/" + id), moderator, "{\"surnames\":\"Gil\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("givenName"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void renameOfAMemberOfAnotherCommunityAnswers404() throws Exception {
        UUID foreign = fixture.member(away, "Luis");

        mockMvc.perform(asJson(put("/api/members/" + foreign), moderator, "{\"givenName\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void renamingALinkedMemberNeverChangesTheUserName() throws Exception {
        UUID member = fixture.member(home, "Ana");
        UUID user = fixture.user(UserRole.REGISTERED, home);
        jdbc.update("update community_member set user_id = ? where id = ?", user, member);
        String before = jdbc.queryForObject("select name from identity_user where id = ?", String.class, user);

        mockMvc.perform(asJson(put("/api/members/" + member), moderator, "{\"givenName\":\"Beatriz\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select name from identity_user where id = ?", String.class, user))
                .isEqualTo(before);
    }

    @Test
    void listReturnsTheCommunityOrderedBySurnamesWithOnlyConsentedContacts() throws Exception {
        createMember(moderator, memberBody(home, "Ana", "Zapata", mobileOnly("600000001"), VERBAL));
        createMember(moderator, memberBody(home, "Luis", "Álvarez", null, null));
        createMember(moderator, memberBody(home, "Marta", null, null, null));
        fixture.member(away, "Ajeno");

        mockMvc.perform(as(get("/api/members"), moderator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].surnames").value("Álvarez"))
                .andExpect(jsonPath("$[1].givenName").value("Marta"))
                .andExpect(jsonPath("$[2].mobile").value("600000001"))
                .andExpect(jsonPath("$[0].mobile").doesNotExist());
    }

    @Test
    void anAdministratorMustSaySomeCommunityToList() throws Exception {
        mockMvc.perform(as(get("/api/members"), administrator))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("communityId"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void anAdministratorListsTheCommunityTheyAskFor() throws Exception {
        fixture.member(away, "Ajeno");

        mockMvc.perform(as(get("/api/members?communityId=" + away), administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].givenName").value("Ajeno"));
    }

    @Test
    void aModeratorAskingForAnotherCommunityGets403() throws Exception {
        mockMvc.perform(as(get("/api/members?communityId=" + away), moderator))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/members")).andExpect(status().isUnauthorized());
    }

    @Test
    void getReturnsOnlyNameCommunityAndConsentedContacts() throws Exception {
        String id = createMember(moderator, memberBody(home, "Ana", "Pérez", mobileOnly("600000001"), VERBAL));

        mockMvc.perform(as(get("/api/members/" + id), moderator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.givenName").value("Ana"))
                .andExpect(jsonPath("$.surnames").value("Pérez"))
                .andExpect(jsonPath("$.communityId").value(home.toString()))
                .andExpect(jsonPath("$.mobile").value("600000001"))
                .andExpect(jsonPath("$.consents").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void getOfAMemberOfAnotherCommunityIsIndistinguishableFromAMissingOne() throws Exception {
        UUID foreign = fixture.member(away, "Luis");

        String foreignBody = mockMvc.perform(as(get("/api/members/" + foreign), moderator))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missingBody = mockMvc.perform(as(get("/api/members/" + UUID.randomUUID()), moderator))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(foreignBody).isEqualTo(missingBody);
    }

    @Test
    void getFlagsTheResponsibleOfTheCommunity() throws Exception {
        UUID member = fixture.member(home, "Ana");
        fixture.designateResponsible(home, member);

        mockMvc.perform(as(get("/api/members/" + member), moderator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isResponsible").value(true));
    }
}
