package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
class CommunityIsolationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final String NO_CONTACT =
            "{\"mobile\":{\"shared\":false},\"landline\":{\"shared\":false},\"email\":{\"shared\":false}}";

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

    private UUID home;
    private UUID away;
    private UUID homeMember;
    private UUID awayMember;
    private UUID homeUser;
    private UUID awayUser;
    private String homeRegistered;
    private String homeModerator;
    private String awayRegistered;
    private String awayModerator;
    private String administrator;

    @BeforeEach
    void setUp() {
        CommunityApiFixture fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        home = fixture.community(1);
        away = fixture.community(2);
        homeMember = fixture.member(home, "Ana");
        awayMember = fixture.member(away, "Luis");
        homeUser = fixture.user(UserRole.REGISTERED, home);
        awayUser = fixture.user(UserRole.REGISTERED, away);
        homeRegistered = fixture.bearer(UserRole.REGISTERED, home);
        homeModerator = fixture.bearer(UserRole.MODERATOR, home);
        awayRegistered = fixture.bearer(UserRole.REGISTERED, away);
        awayModerator = fixture.bearer(UserRole.MODERATOR, away);
        administrator = fixture.bearer(UserRole.ADMINISTRATOR, home);
    }

    private static Arguments op(String label, HttpMethod method, String path, String body, UserRole minimumRole) {
        return Arguments.of(label, method, path, body, minimumRole);
    }

    static Stream<Arguments> operationsOnATargetCommunity() {
        return Stream.of(
                op("getCommunity", HttpMethod.GET, "/api/communities/{community}", null, UserRole.MODERATOR),
                op("updateCommunity", HttpMethod.PUT, "/api/communities/{community}", "{\"number\":77}",
                        UserRole.ADMINISTRATOR),
                op("deleteCommunity", HttpMethod.DELETE, "/api/communities/{community}", null,
                        UserRole.ADMINISTRATOR),
                op("deactivateCommunity", HttpMethod.POST, "/api/communities/{community}/deactivation",
                        "{\"confirmed\":true}", UserRole.ADMINISTRATOR),
                op("reactivateCommunity", HttpMethod.POST, "/api/communities/{community}/reactivation", null,
                        UserRole.ADMINISTRATOR),
                op("designateResponsible", HttpMethod.PUT, "/api/communities/{community}/responsible",
                        "{\"memberId\":\"{member}\"}", UserRole.ADMINISTRATOR),
                op("listMembers", HttpMethod.GET, "/api/members?communityId={community}", null, UserRole.REGISTERED),
                op("createMember", HttpMethod.POST, "/api/members",
                        "{\"communityId\":\"{community}\",\"givenName\":\"Rosa\"}", UserRole.MODERATOR),
                op("getMember", HttpMethod.GET, "/api/members/{member}", null, UserRole.REGISTERED),
                op("renameMember", HttpMethod.PUT, "/api/members/{member}", "{\"givenName\":\"Rosa\"}",
                        UserRole.MODERATOR),
                op("deleteMember", HttpMethod.DELETE, "/api/members/{member}", null, UserRole.MODERATOR),
                op("changeMemberContact", HttpMethod.PUT, "/api/members/{member}/contact", NO_CONTACT,
                        UserRole.MODERATOR),
                op("transferMember", HttpMethod.POST, "/api/members/{member}/transfer",
                        "{\"communityId\":\"{other}\"}", UserRole.ADMINISTRATOR),
                op("linkMember", HttpMethod.PUT, "/api/members/{member}/link", "{\"userId\":\"{user}\"}",
                        UserRole.MODERATOR),
                op("unlinkMember", HttpMethod.DELETE, "/api/members/{member}/link", null, UserRole.MODERATOR),
                op("exportMemberData", HttpMethod.GET, "/api/members/{member}/data-export", null,
                        UserRole.MODERATOR));
    }

    static Stream<Arguments> operationsWithoutATarget() {
        return Stream.of(
                op("listCommunities", HttpMethod.GET, "/api/communities", null, UserRole.REGISTERED),
                op("createCommunity", HttpMethod.POST, "/api/communities", "{\"number\":50}", UserRole.ADMINISTRATOR),
                op("getMyMember", HttpMethod.GET, "/api/me/member", null, UserRole.REGISTERED),
                op("changeMyContact", HttpMethod.PUT, "/api/me/member/contact", NO_CONTACT, UserRole.REGISTERED));
    }

    static Stream<Arguments> allOperations() {
        return Stream.concat(operationsOnATargetCommunity(), operationsWithoutATarget());
    }

    private MockHttpServletRequestBuilder build(HttpMethod method, String path, String body, String bearer,
            UUID community, UUID member, UUID user, UUID other) {
        String resolved = path.replace("{community}", community.toString()).replace("{member}", member.toString())
                .replace("{user}", user.toString()).replace("{other}", other.toString());
        MockHttpServletRequestBuilder builder = request(method, resolved);
        if (bearer != null) {
            builder = builder.header(HttpHeaders.AUTHORIZATION, bearer);
        }
        return body == null ? builder
                : builder.contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("{member}", member.toString()).replace("{community}",
                                community.toString()).replace("{user}", user.toString())
                                .replace("{other}", other.toString()));
    }

    private int statusOnHome(HttpMethod method, String path, String body, String bearer) throws Exception {
        return mockMvc.perform(build(method, path, body, bearer, home, homeMember, homeUser, away)).andReturn()
                .getResponse().getStatus();
    }

    private int statusOnAway(HttpMethod method, String path, String body, String bearer) throws Exception {
        return mockMvc.perform(build(method, path, body, bearer, away, awayMember, awayUser, home)).andReturn()
                .getResponse().getStatus();
    }

    private Map<String, Object> snapshot() {
        return Map.of(
                "members", jdbc.queryForList("select * from community_member order by id"),
                "communities", jdbc.queryForList("select * from community order by id"),
                "consents", jdbc.queryForList("select * from community_contact_consent order by id"),
                "users", jdbc.queryForList("select id, role, community_id from identity_user order by id"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allOperations")
    void visitorsAreUnauthorizedOnEveryOperation(String label, HttpMethod method, String path, String body,
            UserRole minimumRole) throws Exception {
        Map<String, Object> before = snapshot();

        assertThat(statusOnHome(method, path, body, null)).isEqualTo(401);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operationsOnATargetCommunity")
    void registeredUsersAndModeratorsNeverReachAnotherCommunity(String label, HttpMethod method, String path,
            String body, UserRole minimumRole) throws Exception {
        Map<String, Object> before = snapshot();

        for (String bearer : List.of(awayRegistered, awayModerator)) {
            assertThat(statusOnHome(method, path, body, bearer)).as(label + " from away on home")
                    .isIn(403, 404);
        }
        for (String bearer : List.of(homeRegistered, homeModerator)) {
            assertThat(statusOnAway(method, path, body, bearer)).as(label + " from home on away")
                    .isIn(403, 404);
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allOperations")
    void operationsAboveTheRoleAreForbiddenAndChangeNothing(String label, HttpMethod method, String path,
            String body, UserRole minimumRole) throws Exception {
        Map<String, Object> before = snapshot();

        if (minimumRole.ordinal() > UserRole.REGISTERED.ordinal()) {
            assertThat(statusOnHome(method, path, body, homeRegistered)).as(label + " as registered").isEqualTo(403);
        }
        if (minimumRole == UserRole.ADMINISTRATOR) {
            assertThat(statusOnHome(method, path, body, homeModerator)).as(label + " as moderator").isEqualTo(403);
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allOperations")
    void administratorsReachEveryOperation(String label, HttpMethod method, String path, String body,
            UserRole minimumRole) throws Exception {
        assertThat(statusOnHome(method, path, body, administrator)).as(label).isNotIn(401, 403);
    }
}
