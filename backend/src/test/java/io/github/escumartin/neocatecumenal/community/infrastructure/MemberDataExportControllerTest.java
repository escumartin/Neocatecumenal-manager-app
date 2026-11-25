package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
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
class MemberDataExportControllerTest {

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
    private UUID abroad;
    private UUID member;
    private UUID moderatorId;
    private String moderator;
    private String administrator;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        home = fixture.community(7);
        abroad = fixture.community(8);
        member = fixture.member(home, "Ana");
        moderatorId = fixture.user(UserRole.MODERATOR, home);
        moderator = fixture.bearerOf(moderatorId);
        administrator = fixture.bearer(UserRole.ADMINISTRATOR, home);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String bearer) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer);
    }

    private MockHttpServletRequestBuilder contact(String bearer, String mobile) {
        return as(put("/api/members/" + member + "/contact"), bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mobile\":{\"shared\":true,\"value\":\"" + mobile + "\"},\"landline\":"
                        + "{\"shared\":false},\"email\":{\"shared\":false},\"onBehalfConsent\":"
                        + "{\"channel\":\"VERBAL\",\"noticeGiven\":true}}");
    }

    private void consent(UUID memberId, String field, String decision, String channel, UUID by, String role,
            String at) {
        jdbc.update("insert into community_contact_consent (id, member_id, field, decision, channel, "
                + "recorded_by_user_id, recorder_role, notice_given, recorded_at) values (?, ?, ?, ?, ?, ?, ?, "
                + "true, ?::timestamptz)", UUID.randomUUID(), memberId, field, decision, channel, by, role, at);
    }

    @Test
    void exportsTheMemberTheCommunityNumberAndEveryConsentRecordWithItsAuthor() throws Exception {
        UUID ownUser = fixture.user(UserRole.REGISTERED, home);
        jdbc.update("update community_member set user_id = ?, mobile = '600000001' where id = ?", ownUser, member);
        consent(member, "MOBILE", "GRANTED", "VERBAL", moderatorId, "MODERATOR", "2026-11-20T10:00:00Z");
        consent(member, "EMAIL", "GRANTED", "APP", ownUser, "MEMBER", "2026-11-21T10:00:00Z");
        consent(member, "EMAIL", "WITHDRAWN", "APP", ownUser, "MEMBER", "2026-11-22T10:00:00Z");

        mockMvc.perform(as(get("/api/members/" + member + "/data-export"), moderator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.id").value(member.toString()))
                .andExpect(jsonPath("$.member.givenName").value("Ana"))
                .andExpect(jsonPath("$.member.mobile").value("600000001"))
                .andExpect(jsonPath("$.member.linkedUserId").value(ownUser.toString()))
                .andExpect(jsonPath("$.communityNumber").value(7))
                .andExpect(jsonPath("$.exportedAt").exists())
                .andExpect(jsonPath("$.consentRecords", hasSize(3)))
                .andExpect(jsonPath("$.consentRecords[0].field").value("MOBILE"))
                .andExpect(jsonPath("$.consentRecords[0].decision").value("GRANTED"))
                .andExpect(jsonPath("$.consentRecords[0].channel").value("VERBAL"))
                .andExpect(jsonPath("$.consentRecords[0].noticeGiven").value(true))
                .andExpect(jsonPath("$.consentRecords[0].recordedAt").exists())
                .andExpect(jsonPath("$.consentRecords[0].recordedBy.role").value("MODERATOR"))
                .andExpect(jsonPath("$.consentRecords[0].recordedBy.name").value("Test Person"))
                .andExpect(jsonPath("$.consentRecords[0].recordedBy.deleted").value(false))
                .andExpect(jsonPath("$.consentRecords[1].recordedBy.role").value("MEMBER"))
                .andExpect(jsonPath("$.consentRecords[1].recordedBy.name").doesNotExist())
                .andExpect(jsonPath("$.consentRecords[1].recordedBy.deleted").value(false))
                .andExpect(jsonPath("$.consentRecords[2].decision").value("WITHDRAWN"));
    }

    @Test
    void theConsentRecordCarriesNoContactValues() throws Exception {
        jdbc.update("update community_member set mobile = '600000001' where id = ?", member);
        consent(member, "MOBILE", "GRANTED", "VERBAL", moderatorId, "MODERATOR", "2026-11-20T10:00:00Z");

        mockMvc.perform(as(get("/api/members/" + member + "/data-export"), moderator))
                .andExpect(jsonPath("$.consentRecords[0].value").doesNotExist())
                .andExpect(jsonPath("$.consentRecords[0].mobile").doesNotExist());
    }

    @Test
    void aDeletedRecorderShowsAsDeletedWithoutName() throws Exception {
        UUID formerModerator = fixture.user(UserRole.MODERATOR, home);
        consent(member, "MOBILE", "GRANTED", "VERBAL", formerModerator, "MODERATOR", "2026-11-20T10:00:00Z");
        mockMvc.perform(as(delete("/api/users/" + formerModerator), administrator)).andExpect(status().isNoContent());

        mockMvc.perform(as(get("/api/members/" + member + "/data-export"), moderator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRecords[0].recordedBy.role").value("MODERATOR"))
                .andExpect(jsonPath("$.consentRecords[0].recordedBy.deleted").value(true))
                .andExpect(jsonPath("$.consentRecords[0].recordedBy.name").doesNotExist());
    }

    @Test
    void administratorExportsAMemberOfAnyCommunity() throws Exception {
        UUID foreign = fixture.member(abroad, "Luis");

        mockMvc.perform(as(get("/api/members/" + foreign + "/data-export"), administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communityNumber").value(8));
    }

    @Test
    void moderatorOfAnotherCommunityGetsNotFound() throws Exception {
        UUID foreign = fixture.member(abroad, "Luis");

        mockMvc.perform(as(get("/api/members/" + foreign + "/data-export"), moderator))
                .andExpect(status().isNotFound());
    }

    @Test
    void registeredUserIsForbidden() throws Exception {
        mockMvc.perform(as(get("/api/members/" + member + "/data-export"),
                fixture.bearer(UserRole.REGISTERED, home))).andExpect(status().isForbidden());
    }

    @Test
    void missingMemberGetsNotFound() throws Exception {
        mockMvc.perform(as(get("/api/members/" + UUID.randomUUID() + "/data-export"), moderator))
                .andExpect(status().isNotFound());
    }

    @Test
    void withoutSessionAnswers401() throws Exception {
        mockMvc.perform(get("/api/members/" + member + "/data-export")).andExpect(status().isUnauthorized());
    }

    @Test
    void rectifyingAValueLeavesNoTraceOfThePreviousOneAnywhere() throws Exception {
        mockMvc.perform(contact(moderator, "600000001")).andExpect(status().isOk());
        mockMvc.perform(contact(moderator, "600000002")).andExpect(status().isOk());

        mockMvc.perform(as(get("/api/members/" + member + "/data-export"), moderator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.mobile").value("600000002"))
                .andExpect(jsonPath("$.consentRecords", hasSize(1)));
        List<String> stored = jdbc.queryForList("select m::text from community_member m where id = ? union all "
                + "select c::text from community_contact_consent c where member_id = ?", String.class, member,
                member);
        assertThat(stored).noneMatch(row -> row.contains("600000001"));
        assertThat(stored).anyMatch(row -> row.contains("600000002"));
    }
}
