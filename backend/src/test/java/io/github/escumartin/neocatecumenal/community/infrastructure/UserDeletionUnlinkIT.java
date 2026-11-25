package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.escumartin.neocatecumenal.identity.UserDeleted;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserDeletionUnlinkIT {

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

    @Autowired
    private ApplicationContext context;

    private CommunityApiFixture fixture;
    private UUID community;
    private String administrator;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        community = fixture.community(1);
        administrator = fixture.bearer(UserRole.ADMINISTRATOR, community);
    }

    private UUID linkedUserOf(UUID member) {
        return jdbc.queryForObject("select user_id from community_member where id = ?", UUID.class, member);
    }

    @Test
    void deletingALinkedUserKeepsTheMemberWithoutLinkAndWithItsName() throws Exception {
        UUID member = fixture.member(community, "Ana");
        UUID user = fixture.user(UserRole.REGISTERED, community);
        jdbc.update("update community_member set user_id = ? where id = ?", user, member);

        mockMvc.perform(delete("/api/users/" + user).header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from identity_user where id = ?", Integer.class, user))
                .isZero();
        assertThat(linkedUserOf(member)).isNull();
        assertThat(jdbc.queryForObject("select given_name from community_member where id = ?", String.class,
                member)).isEqualTo("Ana");
    }

    @Test
    void whenIdentityRejectsTheDeletionTheLinkStays() throws Exception {
        UUID member = fixture.member(community, "Ana");
        UUID moderator = fixture.user(UserRole.MODERATOR, community);
        jdbc.update("update community_member set user_id = ? where id = ?", moderator, member);

        mockMvc.perform(delete("/api/users/" + moderator).header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_MODERATOR"));

        assertThat(linkedUserOf(member)).isEqualTo(moderator);
    }

    @Test
    void communityDoesNotListenToUserDeleted() {
        boolean listens = Arrays.stream(context.getBeanDefinitionNames())
                .filter(name -> name.toLowerCase().contains("community"))
                .map(name -> context.getType(name))
                .filter(type -> type != null)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(Method::getParameterTypes)
                .anyMatch(parameters -> Arrays.asList(parameters).contains(UserDeleted.class));

        assertThat(listens).isFalse();
    }
}
