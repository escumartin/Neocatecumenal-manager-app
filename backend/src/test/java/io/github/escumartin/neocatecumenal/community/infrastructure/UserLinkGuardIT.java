package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class UserLinkGuardIT {

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
    private String administrator;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        communityA = fixture.community(1);
        communityB = fixture.community(2);
        administrator = fixture.bearer(UserRole.ADMINISTRATOR, communityA);
    }

    private MockHttpServletRequestBuilder moveUser(UUID userId, UUID communityId) {
        return put("/api/users/" + userId).header(HttpHeaders.AUTHORIZATION, administrator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Person\",\"role\":\"REGISTERED\",\"communityId\":\"" + communityId + "\"}");
    }

    private UUID communityOfUser(UUID userId) {
        return jdbc.queryForObject("select community_id from identity_user where id = ?", UUID.class, userId);
    }

    @Test
    void changingTheCommunityOfALinkedUserIsBlocked() throws Exception {
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        jdbc.update("update community_member set user_id = ? where id = ?", user,
                fixture.member(communityA, "Ana"));

        mockMvc.perform(moveUser(user, communityB))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_LINKED_TO_MEMBER"));

        assertThat(communityOfUser(user)).isEqualTo(communityA);
    }

    @Test
    void changingTheCommunityIsAllowedOnceTheLinkedMemberIsInTheTargetCommunity() throws Exception {
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        jdbc.update("update community_member set user_id = ? where id = ?", user,
                fixture.member(communityB, "Ana"));

        mockMvc.perform(moveUser(user, communityB)).andExpect(status().isOk());

        assertThat(communityOfUser(user)).isEqualTo(communityB);
    }

    @Test
    void changingTheCommunityOfAnUnlinkedUserIsAllowed() throws Exception {
        UUID user = fixture.user(UserRole.REGISTERED, communityA);

        mockMvc.perform(moveUser(user, communityB)).andExpect(status().isOk());

        assertThat(communityOfUser(user)).isEqualTo(communityB);
    }

    @Test
    void changingOnlyTheNameOfALinkedUserIsAllowed() throws Exception {
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        jdbc.update("update community_member set user_id = ? where id = ?", user,
                fixture.member(communityA, "Ana"));

        mockMvc.perform(moveUser(user, communityA)).andExpect(status().isOk());
    }

    @Test
    void deletingALinkedUserUnlinksItsMemberFirstAndKeepsTheMemberName() throws Exception {
        UUID user = fixture.user(UserRole.REGISTERED, communityA);
        UUID member = fixture.member(communityA, "Ana");
        jdbc.update("update community_member set user_id = ? where id = ?", user, member);

        mockMvc.perform(delete("/api/users/" + user).header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select user_id from community_member where id = ?", UUID.class, member))
                .isNull();
        assertThat(jdbc.queryForObject("select given_name from community_member where id = ?", String.class,
                member)).isEqualTo("Ana");
    }

    @Test
    void aRejectedUserDeletionKeepsTheLink() throws Exception {
        UUID lastModerator = fixture.user(UserRole.MODERATOR, communityA);
        UUID member = fixture.member(communityA, "Ana");
        jdbc.update("update community_member set user_id = ? where id = ?", lastModerator, member);

        mockMvc.perform(delete("/api/users/" + lastModerator).header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_MODERATOR"));

        assertThat(jdbc.queryForObject("select user_id from community_member where id = ?", UUID.class, member))
                .isEqualTo(lastModerator);
    }
}
