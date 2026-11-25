package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
abstract class AbstractRaceIT {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private UserRepository users;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private SessionTokenIssuer tokens;

    protected CommunityApiFixture fixture;
    protected RaceHarness harness;
    protected UUID communityA;
    protected UUID communityB;
    protected UUID administratorId;

    @BeforeEach
    void setUpRace() {
        fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        communityA = fixture.community(1);
        communityB = fixture.community(2);
        administratorId = fixture.user(UserRole.ADMINISTRATOR, communityA);
        harness = new RaceHarness(mockMvc, jdbc, transaction);
        harness.startWatchingDeadlocks();
    }

    protected String administratorSession() {
        return fixture.bearerOf(administratorId);
    }

    protected Callable<RaceHarness.Response> link(String bearer, UUID member, UUID user) {
        return () -> harness.send(json(put("/api/members/" + member + "/link"), bearer,
                "{\"userId\":\"" + user + "\"}"));
    }

    protected Callable<RaceHarness.Response> transfer(String bearer, UUID member, UUID community) {
        return () -> harness.send(json(post("/api/members/" + member + "/transfer"), bearer,
                "{\"communityId\":\"" + community + "\"}"));
    }

    protected Callable<RaceHarness.Response> deleteUser(String bearer, UUID user) {
        return () -> harness.send(delete("/api/users/" + user).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    protected Callable<RaceHarness.Response> changeUser(String bearer, UUID user, UserRole role, UUID community) {
        return () -> harness.send(json(put("/api/users/" + user), bearer, "{\"name\":\"Test Person\",\"role\":\""
                + role.name() + "\",\"communityId\":\"" + community + "\"}"));
    }

    protected Callable<RaceHarness.Response> deactivate(String bearer, UUID community) {
        return () -> harness.send(json(post("/api/communities/" + community + "/deactivation"), bearer,
                "{\"confirmed\":true}"));
    }

    protected void linkInDatabase(UUID member, UUID user) {
        jdbc.update("update community_member set user_id = ? where id = ?", user, member);
    }

    protected UUID linkedUserOf(UUID member) {
        return jdbc.queryForObject("select user_id from community_member where id = ?", UUID.class, member);
    }

    protected UUID communityOfMember(UUID member) {
        return jdbc.queryForObject("select community_id from community_member where id = ?", UUID.class, member);
    }

    protected UUID communityOfUser(UUID user) {
        return jdbc.queryForObject("select community_id from identity_user where id = ?", UUID.class, user);
    }

    protected boolean userExists(UUID user) {
        return jdbc.queryForObject("select count(*) from identity_user where id = ?", Integer.class, user) > 0;
    }

    protected int danglingLinks() {
        return jdbc.queryForObject("select count(*) from community_member m where m.user_id is not null and not "
                + "exists (select 1 from identity_user u where u.id = m.user_id)", Integer.class);
    }

    protected int linksAcrossCommunities() {
        return jdbc.queryForObject("select count(*) from community_member m join identity_user u on "
                + "u.id = m.user_id where u.community_id <> m.community_id", Integer.class);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String bearer,
            String body) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
