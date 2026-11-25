package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import io.github.escumartin.neocatecumenal.identity.TestCommunityProvisionerConfig;
import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.port.in.DeleteUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UpdateUser;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.IssuedSessionToken;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordHash;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestCommunityProvisionerConfig.class)
class TransactionRetryTest {

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
    private JdbcTemplate jdbc;

    @MockitoBean
    private UpdateUser updateUser;

    @MockitoBean
    private DeleteUser deleteUser;

    private User administrator;
    private String bearer;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
        Community community = communities.save(new Community(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(1)));
        administrator = users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(777002),
                new DisplayName("Test Admin"), UserRole.ADMINISTRATOR, community.id(),
                new PasswordHash("{argon2}hash"), false, new PrivacyConsent("v1", Instant.now())));
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(administrator.id(), issued.hash(), Instant.now()));
        bearer = "Bearer " + issued.token();
    }

    private MockHttpServletRequestBuilder update() {
        return put("/api/users/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Person\",\"role\":\"REGISTERED\",\"communityId\":\""
                        + administrator.communityId().value() + "\"}");
    }

    @Test
    void updateIsCalledThreeTimesAndThenAnswersConcurrentModification() throws Exception {
        when(updateUser.update(any())).thenThrow(new StaleLockOrderException());

        mockMvc.perform(update())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        verify(updateUser, times(3)).update(any());
    }

    @Test
    void updateSucceedsOnTheSecondCallAfterASingleStaleLockOrder() throws Exception {
        User updated = TestUsers.newUser(administrator.id(), administrator.accessId(), new DisplayName("Test Person"),
                UserRole.REGISTERED, administrator.communityId(), new PasswordHash("{argon2}hash"), false,
                new PrivacyConsent("v1", Instant.now()));
        when(updateUser.update(any())).thenThrow(new StaleLockOrderException()).thenReturn(updated);

        mockMvc.perform(update()).andExpect(status().isOk());

        verify(updateUser, times(2)).update(any());
    }

    @Test
    void deleteIsCalledThreeTimesAndThenAnswersConcurrentModification() throws Exception {
        doThrow(new StaleLockOrderException()).when(deleteUser).delete(any());

        mockMvc.perform(delete("/api/users/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        verify(deleteUser, times(3)).delete(any());
    }

    @Test
    void deleteSucceedsOnTheSecondCallAfterASingleStaleLockOrder() throws Exception {
        doThrow(new StaleLockOrderException()).doNothing().when(deleteUser).delete(any());

        mockMvc.perform(delete("/api/users/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNoContent());

        verify(deleteUser, times(2)).delete(any());
    }
}
