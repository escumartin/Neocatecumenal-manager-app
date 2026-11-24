package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.escumartin.neocatecumenal.identity.TestUsers;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ConcurrentModificationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private UserRepository users;

    @Autowired
    private CommunityRepository communities;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private SessionTokenIssuer tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Community community;

    @BeforeEach
    void setUp() {
        Mockito.reset(users);
        jdbc.execute("truncate identity_login_pair_throttle, identity_login_ip_throttle, identity_session, "
                + "identity_user, identity_community cascade");
        community = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(1)));
    }

    private User user(UserRole role, int accessId) {
        return users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(accessId),
                new DisplayName("Test Person"), role, community.id(), new PasswordHash("{argon2}hash"), false,
                new PrivacyConsent("v1", Instant.now())));
    }

    @Test
    void anEditThatLosesTheRaceAgainstAnotherWriterConflictsAndKeepsTheOtherWrite() throws Exception {
        User administrator = user(UserRole.ADMINISTRATOR, 610001);
        User target = user(UserRole.REGISTERED, 610002);
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(administrator.id(), issued.hash(), Instant.now()));
        doAnswer(invocation -> {
            new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW)).executeWithoutResult(status -> jdbc.update(
                    "update identity_user set name = 'Written First', version = version + 1 where id = ?",
                    target.id().value()));
            return invocation.callRealMethod();
        }).when(users).save(any(User.class));

        mockMvc.perform(put("/api/users/" + target.id().value())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + issued.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Written Second\",\"role\":\"REGISTERED\",\"communityId\":\""
                                + community.id().value() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        Mockito.reset(users);

        assertThat(
                jdbc.queryForObject("select name from identity_user where id = ?", String.class,
                        target.id().value())).isEqualTo("Written First");
    }
}
