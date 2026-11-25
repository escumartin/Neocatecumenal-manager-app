package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class LinkTransferControllerIT {

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
    private UUID member;
    private String administrator;
    private String moderatorOfA;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        communityA = fixture.community(1);
        communityB = fixture.community(2);
        member = fixture.member(communityA, "Ana");
        administrator = fixture.bearer(UserRole.ADMINISTRATOR, communityA);
        moderatorOfA = fixture.bearer(UserRole.MODERATOR, communityA);
    }

    private MockHttpServletRequestBuilder asJson(MockHttpServletRequestBuilder builder, String bearer, String body) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String bearer) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer);
    }

    private MockHttpServletRequestBuilder link(String bearer, UUID memberId, UUID userId) {
        return asJson(put("/api/members/" + memberId + "/link"), bearer, "{\"userId\":\"" + userId + "\"}");
    }

    private MockHttpServletRequestBuilder transfer(String bearer, UUID memberId, UUID communityId) {
        return asJson(post("/api/members/" + memberId + "/transfer"), bearer,
                "{\"communityId\":\"" + communityId + "\"}");
    }

    private UUID linkedUser(UUID memberId) {
        return jdbc.queryForObject("select user_id from community_member where id = ?", UUID.class, memberId);
    }

    private UUID communityOfMember(UUID memberId) {
        return jdbc.queryForObject("select community_id from community_member where id = ?", UUID.class, memberId);
    }

    private UUID communityOfUser(UUID userId) {
        return jdbc.queryForObject("select community_id from identity_user where id = ?", UUID.class, userId);
    }

    private void linkInDatabase(UUID memberId, UUID userId) {
        jdbc.update("update community_member set user_id = ? where id = ?", userId, memberId);
    }

    @Test
    void moderatorLinksARegisteredUserOfTheirCommunity() throws Exception {
        UUID user = fixture.user(UserRole.REGISTERED, communityA);

        mockMvc.perform(link(moderatorOfA, member, user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedUserId").value(user.toString()));

        assertThat(linkedUser(member)).isEqualTo(user);
    }

    @Test
    void linkingCopiesNoNameBetweenMemberAndUser() throws Exception {
        UUID user = fixture.user(UserRole.REGISTERED, communityA);

        mockMvc.perform(link(moderatorOfA, member, user)).andExpect(status().isOk());
        mockMvc.perform(asJson(put("/api/members/" + member), moderatorOfA,
                "{\"givenName\":\"Renamed\",\"surnames\":\"Member\"}")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select name from identity_user where id = ?", String.class, user))
                .isEqualTo("Test Person");
        assertThat(jdbc.queryForObject("select given_name from community_member where id = ?", String.class,
                member)).isEqualTo("Renamed");
    }

    @Test
    void moderatorLinkingAModeratorIsForbidden() throws Exception {
        UUID otherModerator = fixture.user(UserRole.MODERATOR, communityA);

        mockMvc.perform(link(moderatorOfA, member, otherModerator)).andExpect(status().isForbidden());

        assertThat(linkedUser(member)).isNull();
    }

    @Test
    void moderatorLinkingAnAdministratorIsForbidden() throws Exception {
        UUID otherAdministrator = fixture.user(UserRole.ADMINISTRATOR, communityA);

        mockMvc.perform(link(moderatorOfA, member, otherAdministrator)).andExpect(status().isForbidden());
    }

    @Test
    void moderatorLinkingAUserOfAnotherCommunityIsForbidden() throws Exception {
        UUID foreign = fixture.user(UserRole.REGISTERED, communityB);

        mockMvc.perform(link(moderatorOfA, member, foreign)).andExpect(status().isForbidden());
    }

    @Test
    void administratorLinkingAUserOfAnotherCommunityIsUnprocessable() throws Exception {
        UUID foreign = fixture.user(UserRole.REGISTERED, communityB);

        mockMvc.perform(link(administrator, member, foreign))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("userId"))
                .andExpect(jsonPath("$.errors[0].code").value("USER_NOT_IN_COMMUNITY"));
    }

    @Test
    void linkingAMissingUserIsUnprocessable() throws Exception {
        mockMvc.perform(link(administrator, member, UUID.randomUUID()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].code").value("USER_NOT_FOUND"));
    }

    @Test
    void linkingAnAlreadyLinkedMemberConflicts() throws Exception {
        linkInDatabase(member, fixture.user(UserRole.REGISTERED, communityA));
        UUID another = fixture.user(UserRole.REGISTERED, communityA);

        mockMvc.perform(link(moderatorOfA, member, another))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_ALREADY_LINKED"));
    }

    @Test
    void linkingAUserLinkedToAnotherMemberConflicts() throws Exception {
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        linkInDatabase(fixture.member(communityA, "Berta"), user);

        mockMvc.perform(link(moderatorOfA, member, user))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_LINKED"));
        assertThat(linkedUser(member)).isNull();
    }

    @Test
    void registeredUsersCannotLink() throws Exception {
        UUID user = fixture.user(UserRole.REGISTERED, communityA);

        mockMvc.perform(link(fixture.bearerOf(user), member, user)).andExpect(status().isForbidden());
    }

    @Test
    void administratorUnlinksAnyLink() throws Exception {
        linkInDatabase(member, fixture.user(UserRole.MODERATOR, communityA));

        mockMvc.perform(as(delete("/api/members/" + member + "/link"), administrator))
                .andExpect(status().isNoContent());

        assertThat(linkedUser(member)).isNull();
    }

    @Test
    void moderatorUnlinksARegisteredUserOfTheirCommunity() throws Exception {
        linkInDatabase(member, fixture.user(UserRole.REGISTERED, communityA));

        mockMvc.perform(as(delete("/api/members/" + member + "/link"), moderatorOfA))
                .andExpect(status().isNoContent());

        assertThat(linkedUser(member)).isNull();
    }

    @Test
    void theLinkedUserCannotUnlinkThemselves() throws Exception {
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        linkInDatabase(member, user);

        mockMvc.perform(as(delete("/api/members/" + member + "/link"), fixture.bearerOf(user)))
                .andExpect(status().isForbidden());

        assertThat(linkedUser(member)).isEqualTo(user);
    }

    @Test
    void moderatorCannotUnlinkAMemberLinkedToAModerator() throws Exception {
        linkInDatabase(member, fixture.user(UserRole.MODERATOR, communityA));

        mockMvc.perform(as(delete("/api/members/" + member + "/link"), moderatorOfA))
                .andExpect(status().isForbidden());
    }

    @Test
    void unlinkingAMemberWithoutUserConflicts() throws Exception {
        mockMvc.perform(as(delete("/api/members/" + member + "/link"), moderatorOfA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_LINKED"));
    }

    @Test
    void administratorTransfersTheMemberAndItsUserTogether() throws Exception {
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        linkInDatabase(member, user);

        mockMvc.perform(transfer(administrator, member, communityB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communityId").value(communityB.toString()));

        assertThat(communityOfMember(member)).isEqualTo(communityB);
        assertThat(communityOfUser(user)).isEqualTo(communityB);
        assertThat(linkedUser(member)).isEqualTo(user);
    }

    @Test
    void transferOfAMemberWithoutUserOnlyMovesTheMember() throws Exception {
        mockMvc.perform(transfer(administrator, member, communityB)).andExpect(status().isOk());

        assertThat(communityOfMember(member)).isEqualTo(communityB);
    }

    @Test
    void moderatorCannotTransfer() throws Exception {
        mockMvc.perform(transfer(moderatorOfA, member, communityB)).andExpect(status().isForbidden());

        assertThat(communityOfMember(member)).isEqualTo(communityA);
    }

    @Test
    void transferToADeactivatedCommunityIsUnprocessableAndMovesNothing() throws Exception {
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        linkInDatabase(member, user);
        jdbc.update("update community set status = 'DEACTIVATED' where id = ?", communityB);
        jdbc.update("update identity_community set active = false where id = ?", communityB);

        mockMvc.perform(transfer(administrator, member, communityB))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("communityId"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_DEACTIVATED"));

        assertThat(communityOfMember(member)).isEqualTo(communityA);
        assertThat(communityOfUser(user)).isEqualTo(communityA);
    }

    @Test
    void transferOfTheResponsibleConflicts() throws Exception {
        fixture.designateResponsible(communityA, member);

        mockMvc.perform(transfer(administrator, member, communityB))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESPONSIBLE_REQUIRED"));

        assertThat(communityOfMember(member)).isEqualTo(communityA);
    }

    @Test
    void transferOfTheLastModeratorConflictsAndChangesNothing() throws Exception {
        jdbc.update("delete from identity_session where user_id in (select id from identity_user where role = "
                + "'MODERATOR')");
        jdbc.update("delete from identity_user where role = 'MODERATOR'");
        UUID lastModerator = fixture.user(UserRole.MODERATOR, communityA);
        linkInDatabase(member, lastModerator);

        mockMvc.perform(transfer(administrator, member, communityB))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_MODERATOR"));

        assertThat(communityOfMember(member)).isEqualTo(communityA);
        assertThat(communityOfUser(lastModerator)).isEqualTo(communityA);
    }

    @Test
    void transferToAMissingCommunityIsNotFound() throws Exception {
        mockMvc.perform(transfer(administrator, member, UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
