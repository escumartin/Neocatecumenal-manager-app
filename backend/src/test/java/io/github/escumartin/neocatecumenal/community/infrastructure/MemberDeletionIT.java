package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MemberDeletionIT {

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
    private UUID communityA;
    private UUID communityB;
    private UUID moderatorId;
    private String moderator;
    private UUID administratorId;
    private String administrator;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        communityA = fixture.community(1);
        communityB = fixture.community(2);
        moderatorId = fixture.user(UserRole.MODERATOR, communityA);
        moderator = fixture.bearerOf(moderatorId);
        administratorId = fixture.user(UserRole.ADMINISTRATOR, communityA);
        administrator = fixture.bearerOf(administratorId);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String bearer) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer);
    }

    private void link(UUID member, UUID user) {
        jdbc.update("update community_member set user_id = ? where id = ?", user, member);
    }

    private void consent(UUID member, String field, UUID recordedBy) {
        jdbc.update("insert into community_contact_consent (id, member_id, field, decision, channel, "
                + "recorded_by_user_id, recorder_role, notice_given, recorded_at) values (?, ?, ?, 'GRANTED', "
                + "'VERBAL', ?, 'MODERATOR', true, now())", UUID.randomUUID(), member, field, recordedBy);
    }

    private int count(String table, String column, UUID id) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?", Integer.class, id);
    }

    @Test
    void moderatorDeletesAnUnlinkedMemberWithItsConsentRecords() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        consent(member, "MOBILE", moderatorId);
        consent(member, "EMAIL", moderatorId);

        mockMvc.perform(as(delete("/api/members/" + member), moderator)).andExpect(status().isNoContent());

        assertThat(count("community_member", "id", member)).isZero();
        assertThat(count("community_contact_consent", "member_id", member)).isZero();
    }

    @Test
    void deletingAMemberLinkedToARegisteredUserDeletesTheUserAndItsSessions() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        String userSession = fixture.bearerOf(user);
        link(member, user);
        mockMvc.perform(as(get("/api/me/member"), userSession)).andExpect(status().isOk());

        mockMvc.perform(as(delete("/api/members/" + member), moderator)).andExpect(status().isNoContent());

        assertThat(count("community_member", "id", member)).isZero();
        assertThat(count("identity_user", "id", user)).isZero();
        mockMvc.perform(as(get("/api/me/member"), userSession)).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingAMemberLeavesTheOtherMembersAndTheirUsers() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID bystander = fixture.member(communityA, "Luis");
        UUID bystanderUser = fixture.user(UserRole.REGISTERED, communityA);
        link(bystander, bystanderUser);

        mockMvc.perform(as(delete("/api/members/" + member), moderator)).andExpect(status().isNoContent());

        assertThat(count("community_member", "id", bystander)).isEqualTo(1);
        assertThat(count("identity_user", "id", bystanderUser)).isEqualTo(1);
    }

    @Test
    void moderatorDeletingTheirOwnMemberIsForbidden() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        link(member, moderatorId);

        mockMvc.perform(as(delete("/api/members/" + member), moderator)).andExpect(status().isForbidden());

        assertThat(count("community_member", "id", member)).isEqualTo(1);
        assertThat(count("identity_user", "id", moderatorId)).isEqualTo(1);
    }

    @Test
    void moderatorDeletingAMemberLinkedToAnotherModeratorIsForbidden() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID otherModerator = fixture.user(UserRole.MODERATOR, communityA);
        link(member, otherModerator);

        mockMvc.perform(as(delete("/api/members/" + member), moderator)).andExpect(status().isForbidden());

        assertThat(count("identity_user", "id", otherModerator)).isEqualTo(1);
    }

    @Test
    void moderatorDeletingAMemberOfAnotherCommunityGetsNotFound() throws Exception {
        UUID foreign = fixture.member(communityB, "Ana");

        mockMvc.perform(as(delete("/api/members/" + foreign), moderator)).andExpect(status().isNotFound());

        assertThat(count("community_member", "id", foreign)).isEqualTo(1);
    }

    @Test
    void aRegisteredUserCannotDeleteMembers() throws Exception {
        UUID member = fixture.member(communityA, "Ana");

        mockMvc.perform(as(delete("/api/members/" + member), fixture.bearer(UserRole.REGISTERED, communityA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingTheResponsibleMemberAnswers409ResponsibleRequired() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        fixture.designateResponsible(communityA, member);

        mockMvc.perform(as(delete("/api/members/" + member), moderator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESPONSIBLE_REQUIRED"));

        assertThat(count("community_member", "id", member)).isEqualTo(1);
    }

    @Test
    void administratorDeletingTheMemberLinkedToThemselvesAnswers409SelfActionForbidden() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        link(member, administratorId);

        mockMvc.perform(as(delete("/api/members/" + member), administrator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ACTION_FORBIDDEN"));

        assertThat(count("community_member", "id", member)).isEqualTo(1);
        assertThat(count("identity_user", "id", administratorId)).isEqualTo(1);
    }

    @Test
    void administratorDeletingTheMemberOfTheLastModeratorAnswers409AndKeepsTheLink() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        link(member, moderatorId);

        mockMvc.perform(as(delete("/api/members/" + member), administrator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_MODERATOR"));

        assertThat(count("community_member", "id", member)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select user_id from community_member where id = ?", UUID.class, member))
                .isEqualTo(moderatorId);
        assertThat(count("identity_user", "id", moderatorId)).isEqualTo(1);
    }

    @Test
    void administratorDeletesAMemberLinkedToAModeratorWhenAnotherOneRemains() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID second = fixture.user(UserRole.MODERATOR, communityA);
        link(member, second);

        mockMvc.perform(as(delete("/api/members/" + member), administrator)).andExpect(status().isNoContent());

        assertThat(count("community_member", "id", member)).isZero();
        assertThat(count("identity_user", "id", second)).isZero();
    }

    @Test
    void theHistoryOfTheDeletedUserIsAnonymized() throws Exception {
        UUID member = fixture.member(communityA, "Ana");
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        link(member, user);
        UUID room = UUID.randomUUID();
        jdbc.update("insert into room (id, name, active, version, created_at) values (?, 'Main hall', true, 0, "
                + "now())", room);
        UUID request = UUID.randomUUID();
        jdbc.update("insert into booking_request (id, room_id, community_id, requested_by, reason, first_date, "
                + "last_date, start_time, end_time, status, version, created_at) values (?, ?, ?, ?, 'Catechesis', "
                + "current_date, current_date, '18:00', '20:00', 'PENDING', 0, now())", request, room, communityA,
                user);
        UUID event = UUID.randomUUID();
        jdbc.update("insert into event (id, community_id, type, title, event_date, start_time, end_time, room_id, "
                + "booking_request_id, created_by, created_at) values (?, ?, 'WORD', 'Word', current_date, "
                + "'18:00', '20:00', ?, ?, ?, now())", event, communityA, room, UUID.randomUUID(), user);

        mockMvc.perform(as(delete("/api/members/" + member), moderator)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select requested_by from booking_request where id = ?", UUID.class,
                request)).isNull();
        assertThat(jdbc.queryForObject("select created_by from event where id = ?", UUID.class, event)).isNull();
    }
}
