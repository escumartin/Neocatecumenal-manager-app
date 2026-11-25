package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
class DirectoryAccessIT {

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
    private UUID away;
    private UUID homeMember;
    private UUID awayMember;
    private String administrator;
    private String moderator;
    private String registered;
    private String awayRegistered;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        home = fixture.community(1);
        away = fixture.community(2);
        homeMember = fixture.member(home, "Ana");
        awayMember = fixture.member(away, "Luis");
        jdbc.update("update community_member set surnames = 'Pérez', mobile = '600000001', email = 'ana@example.org', "
                + "user_id = ? where id = ?", fixture.user(UserRole.REGISTERED, home), homeMember);
        jdbc.update("update community_member set mobile = '600000002' where id = ?", awayMember);
        administrator = fixture.bearer(UserRole.ADMINISTRATOR, home);
        moderator = fixture.bearer(UserRole.MODERATOR, home);
        registered = fixture.bearer(UserRole.REGISTERED, home);
        awayRegistered = fixture.bearer(UserRole.REGISTERED, away);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String bearer) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer);
    }

    @Test
    void aRegisteredUserListsTheirOwnCommunityDirectoryWithoutLinkedUserIds() throws Exception {
        mockMvc.perform(as(get("/api/members"), registered))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].givenName").value("Ana"))
                .andExpect(jsonPath("$[0].mobile").value("600000001"))
                .andExpect(jsonPath("$[0].email").value("ana@example.org"))
                .andExpect(jsonPath("$[0].linkedUserId").doesNotExist());
    }

    @Test
    void aRegisteredUserGetsAMemberOfTheirOwnCommunityWithoutTheLinkedUserId() throws Exception {
        mockMvc.perform(as(get("/api/members/" + homeMember), registered))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.givenName").value("Ana"))
                .andExpect(jsonPath("$.mobile").value("600000001"))
                .andExpect(jsonPath("$.linkedUserId").doesNotExist());
    }

    @Test
    void aModeratorSeesTheSameContactsPlusTheLinkedUserId() throws Exception {
        mockMvc.perform(as(get("/api/members/" + homeMember), moderator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobile").value("600000001"))
                .andExpect(jsonPath("$.email").value("ana@example.org"))
                .andExpect(jsonPath("$.linkedUserId").exists());
    }

    @Test
    void anAdministratorSeesTheLinkedUserIdInTheListOfAnyCommunity() throws Exception {
        mockMvc.perform(as(get("/api/members").param("communityId", home.toString()), administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].linkedUserId").exists());
        mockMvc.perform(as(get("/api/members").param("communityId", away.toString()), administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].givenName").value("Luis"));
    }

    @Test
    void theDirectoryOfAnotherCommunityIsForbiddenForARegisteredUserAndAModerator() throws Exception {
        mockMvc.perform(as(get("/api/members").param("communityId", away.toString()), registered))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/members").param("communityId", away.toString()), moderator))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMemberOfAnotherCommunityAnswers404IdenticalToAMissingOne() throws Exception {
        String foreign = mockMvc.perform(as(get("/api/members/" + awayMember), registered))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(as(get("/api/members/" + UUID.randomUUID()), registered))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(foreign).isEqualTo(missing);
        mockMvc.perform(as(get("/api/members/" + homeMember), awayRegistered)).andExpect(status().isNotFound());
    }

    @Test
    void visitorsAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/members")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/members/" + homeMember)).andExpect(status().isUnauthorized());
    }

    @Test
    void aDeactivatedCommunityDirectoryAnswers409ForRegisteredUsersAndModeratorsButNotForAdministrators()
            throws Exception {
        jdbc.update("update community set status = 'DEACTIVATED' where id = ?", home);

        mockMvc.perform(as(get("/api/members"), registered)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUNITY_DEACTIVATED"));
        mockMvc.perform(as(get("/api/members"), moderator)).andExpect(status().isConflict());
        mockMvc.perform(as(get("/api/members/" + homeMember), registered)).andExpect(status().isConflict());
        mockMvc.perform(as(get("/api/members").param("communityId", home.toString()), administrator))
                .andExpect(status().isOk());
    }

    @Test
    void anAdministratorWithoutACommunityIdGets422() throws Exception {
        mockMvc.perform(as(get("/api/members"), administrator)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void errorsNeverCarryNamesPhonesOrEmails() throws Exception {
        String forbidden = mockMvc.perform(as(get("/api/members").param("communityId", away.toString()), registered))
                .andReturn().getResponse().getContentAsString();
        String notFound = mockMvc.perform(as(get("/api/members/" + awayMember), registered))
                .andReturn().getResponse().getContentAsString();
        jdbc.update("update community set status = 'DEACTIVATED' where id = ?", home);
        String conflict = mockMvc.perform(as(get("/api/members"), registered))
                .andReturn().getResponse().getContentAsString();

        assertThat(forbidden + notFound + conflict).doesNotContain("Ana", "Luis", "Pérez", "600000001",
                "600000002", "ana@example.org");
    }
}
