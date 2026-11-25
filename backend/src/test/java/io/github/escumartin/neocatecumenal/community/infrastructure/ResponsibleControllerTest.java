package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
class ResponsibleControllerTest {

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
    private String administrator;
    private String moderator;
    private String registered;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        home = fixture.community(12);
        administrator = fixture.bearer(UserRole.ADMINISTRATOR, home);
        moderator = fixture.bearer(UserRole.MODERATOR, home);
        registered = fixture.bearer(UserRole.REGISTERED, home);
    }

    private MockHttpServletRequestBuilder designation(String bearer, UUID community, Object memberId) {
        return put("/api/communities/" + community + "/responsible")
                .header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":" + (memberId == null ? "null" : "\"" + memberId + "\"") + "}");
    }

    private Object responsibleOf(UUID community) {
        return jdbc.queryForObject("select responsible_member_id from community where id = ?", Object.class,
                community);
    }

    @Test
    void aNewCommunityShowsNoResponsibleAndAdmitsMembers() throws Exception {
        mockMvc.perform(get("/api/communities/" + home).header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasResponsible").value(false))
                .andExpect(jsonPath("$.responsible").doesNotExist())
                .andExpect(jsonPath("$.responsibleIsModerator").value(false));

        mockMvc.perform(post("/api/members").header(HttpHeaders.AUTHORIZATION, administrator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"communityId\":\"" + home + "\",\"givenName\":\"Ana\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void theAdministratorDesignatesAMemberOfTheCommunity() throws Exception {
        UUID member = fixture.member(home, "Ana");

        mockMvc.perform(designation(administrator, home, member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasResponsible").value(true))
                .andExpect(jsonPath("$.responsible.id").value(member.toString()))
                .andExpect(jsonPath("$.responsible.givenName").value("Ana"))
                .andExpect(jsonPath("$.responsibleIsModerator").value(false));

        mockMvc.perform(get("/api/communities/" + home).header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(jsonPath("$.hasResponsible").value(true))
                .andExpect(jsonPath("$.responsible.id").value(member.toString()));
    }

    @Test
    void theAdministratorReplacesTheResponsible() throws Exception {
        UUID first = fixture.member(home, "Ana");
        UUID second = fixture.member(home, "Berta");
        fixture.designateResponsible(home, first);

        mockMvc.perform(designation(administrator, home, second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsible.id").value(second.toString()));
    }

    @Test
    void aResponsibleLinkedToAModeratorIsNotFlagged() throws Exception {
        UUID member = fixture.member(home, "Ana");
        UUID moderatorUser = fixture.user(UserRole.MODERATOR, home);
        jdbc.update("update community_member set user_id = ? where id = ?", moderatorUser, member);

        mockMvc.perform(designation(administrator, home, member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsibleIsModerator").value(true));
    }

    @Test
    void aResponsibleLinkedToARegisteredUserIsFlaggedButAccepted() throws Exception {
        UUID member = fixture.member(home, "Ana");
        UUID registeredUser = fixture.user(UserRole.REGISTERED, home);
        jdbc.update("update community_member set user_id = ? where id = ?", registeredUser, member);

        mockMvc.perform(designation(administrator, home, member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responsibleIsModerator").value(false));
    }

    @Test
    void aMemberOfAnotherCommunityIsRejectedWith422AndNothingChanges() throws Exception {
        UUID own = fixture.member(home, "Ana");
        fixture.designateResponsible(home, own);
        UUID otherCommunity = fixture.community(13);
        UUID foreign = fixture.member(otherCommunity, "Carla");

        mockMvc.perform(designation(administrator, home, foreign))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("memberId"))
                .andExpect(jsonPath("$.errors[0].code").value("MEMBER_NOT_IN_COMMUNITY"));

        assertThat(responsibleOf(home)).isEqualTo(own);
    }

    @Test
    void anUnknownMemberIs404() throws Exception {
        mockMvc.perform(designation(administrator, home, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void anUnknownCommunityIs404() throws Exception {
        mockMvc.perform(designation(administrator, UUID.randomUUID(), fixture.member(home, "Ana")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMissingMemberIdIs422() throws Exception {
        mockMvc.perform(designation(administrator, home, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"MODERATOR", "REGISTERED"})
    void nonAdministratorsAreForbiddenAndNothingChanges(UserRole role) throws Exception {
        UUID member = fixture.member(home, "Ana");
        String bearer = role == UserRole.MODERATOR ? moderator : registered;

        mockMvc.perform(designation(bearer, home, member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(responsibleOf(home)).isNull();
    }

    @Test
    void anAnonymousCallIs401() throws Exception {
        mockMvc.perform(put("/api/communities/" + home + "/responsible").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
