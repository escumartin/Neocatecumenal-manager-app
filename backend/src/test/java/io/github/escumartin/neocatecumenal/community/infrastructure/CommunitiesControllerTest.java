package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
class CommunitiesControllerTest {

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
        home = fixture.community(1);
        administrator = fixture.bearer(UserRole.ADMINISTRATOR, home);
        moderator = fixture.bearer(UserRole.MODERATOR, home);
        registered = fixture.bearer(UserRole.REGISTERED, home);
    }

    private MockHttpServletRequestBuilder asJson(MockHttpServletRequestBuilder builder, String bearer, String body) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String bearer) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer);
    }

    private String create(int number, String nameField) throws Exception {
        String body = mockMvc.perform(asJson(post("/api/communities"), administrator,
                "{\"number\":" + number + nameField + "}")).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void deactivate(String id) throws Exception {
        mockMvc.perform(asJson(post("/api/communities/" + id + "/deactivation"), administrator,
                "{\"confirmed\":false}")).andExpect(status().isNoContent());
    }

    @Test
    void listReturnsActiveCommunitiesOrderedByNumberAndHidesDeactivatedOnes() throws Exception {
        String twelve = create(12, "");
        String five = create(5, "");
        deactivate(five);

        mockMvc.perform(as(get("/api/communities"), administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[1].id").value(twelve))
                .andExpect(jsonPath("$[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$[1].hasResponsible").value(false));
    }

    @Test
    void administratorCanListDeactivatedCommunitiesToo() throws Exception {
        String five = create(5, "");
        deactivate(five);

        mockMvc.perform(as(get("/api/communities?includeDeactivated=true"), administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].id").value(five))
                .andExpect(jsonPath("$[1].status").value("DEACTIVATED"));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"REGISTERED", "MODERATOR"})
    void nonAdministratorsListActiveCommunitiesButCannotAskForDeactivatedOnes(UserRole role) throws Exception {
        String bearer = role == UserRole.MODERATOR ? moderator : registered;

        mockMvc.perform(as(get("/api/communities"), bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(as(get("/api/communities?includeDeactivated=true"), bearer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/communities")).andExpect(status().isUnauthorized());
    }

    @Test
    void createReturns201WithLocationAndAnActiveCommunityWithoutResponsible() throws Exception {
        var result = mockMvc.perform(asJson(post("/api/communities"), administrator,
                "{\"number\":12,\"name\":\"San Pedro\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(12))
                .andExpect(jsonPath("$.name").value("San Pedro"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.hasResponsible").value(false))
                .andExpect(jsonPath("$.responsibleIsModerator").value(false))
                .andReturn().getResponse();

        String id = JsonPath.read(result.getContentAsString(), "$.id");
        assertThat(result.getHeader("Location")).endsWith("/api/communities/" + id);
        assertThat(jdbc.queryForObject("select count(*) from identity_community where id = ?::uuid and number = 12",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void createStoresABlankNameAsAbsent() throws Exception {
        mockMvc.perform(asJson(post("/api/communities"), administrator, "{\"number\":7,\"name\":\"   \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").doesNotExist());

        assertThat(jdbc.queryForObject("select name from community where number = 7", String.class)).isNull();
    }

    @Test
    void createRejectsANameOfOneHundredAndOneCharacters() throws Exception {
        mockMvc.perform(asJson(post("/api/communities"), administrator,
                "{\"number\":7,\"name\":\"" + "a".repeat(101) + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
    }

    @Test
    void createAcceptsANameOfExactlyOneHundredCharacters() throws Exception {
        mockMvc.perform(asJson(post("/api/communities"), administrator,
                "{\"number\":7,\"name\":\"" + "a".repeat(100) + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createRejectsAMissingNumber() throws Exception {
        mockMvc.perform(asJson(post("/api/communities"), administrator, "{\"name\":\"San Pedro\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("number"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void createRejectsANumberBelowOne() throws Exception {
        mockMvc.perform(asJson(post("/api/communities"), administrator, "{\"number\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("number"));
    }

    @Test
    void createRejectsADuplicatedNumberOnTheNumberField() throws Exception {
        create(12, "");

        mockMvc.perform(asJson(post("/api/communities"), administrator, "{\"number\":12}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("number"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_NUMBER_TAKEN"));
        assertThat(jdbc.queryForObject("select count(*) from community where number = 12", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void getReturnsTheCommunityFileToAnAdministrator() throws Exception {
        String id = create(12, ",\"name\":\"San Pedro\"");

        mockMvc.perform(as(get("/api/communities/" + id), administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("San Pedro"))
                .andExpect(jsonPath("$.hasResponsible").value(false));
    }

    @Test
    void getLetsAModeratorReadTheirOwnCommunityOnly() throws Exception {
        String other = create(12, "");

        mockMvc.perform(as(get("/api/communities/" + home), moderator)).andExpect(status().isOk());
        mockMvc.perform(as(get("/api/communities/" + other), moderator)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/communities/" + home), registered)).andExpect(status().isForbidden());
    }

    @Test
    void getOfAnUnknownCommunityIsNotFound() throws Exception {
        mockMvc.perform(as(get("/api/communities/" + UUID.randomUUID()), administrator))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void updateRenumbersAndRenamesAndKeepsTheIdentityProjectionInSync() throws Exception {
        String id = create(12, ",\"name\":\"San Pedro\"");

        mockMvc.perform(asJson(put("/api/communities/" + id), administrator,
                "{\"number\":40,\"name\":\"Santa Ana\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(40))
                .andExpect(jsonPath("$.name").value("Santa Ana"));

        assertThat(jdbc.queryForObject("select number from identity_community where id = ?::uuid", Integer.class,
                id)).isEqualTo(40);
    }

    @Test
    void updateWithoutANameClearsIt() throws Exception {
        String id = create(12, ",\"name\":\"San Pedro\"");

        mockMvc.perform(asJson(put("/api/communities/" + id), administrator, "{\"number\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").doesNotExist());
    }

    @Test
    void updateToTheNumberOfAnotherCommunityIsRejectedOnTheNumberField() throws Exception {
        String id = create(12, "");
        create(13, "");

        mockMvc.perform(asJson(put("/api/communities/" + id), administrator, "{\"number\":13}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("number"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_NUMBER_TAKEN"));
    }

    @Test
    void updateOfAnUnknownCommunityIsNotFound() throws Exception {
        mockMvc.perform(asJson(put("/api/communities/" + UUID.randomUUID()), administrator, "{\"number\":3}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteOfAnEmptyCommunityReturns204AndRemovesItFromBothTables() throws Exception {
        String id = create(12, "");

        mockMvc.perform(as(delete("/api/communities/" + id), administrator)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from community where id = ?::uuid", Integer.class, id))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from identity_community where id = ?::uuid",
                Integer.class, id)).isZero();
    }

    @Test
    void deleteWithAResponsibleIsRejected() throws Exception {
        UUID id = fixture.community(12);
        fixture.designateResponsible(id, fixture.member(id, "Test Member"));

        mockMvc.perform(as(delete("/api/communities/" + id), administrator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUNITY_HAS_RESPONSIBLE"));
    }

    @Test
    void deleteWithMembersIsRejected() throws Exception {
        UUID id = fixture.community(12);
        fixture.member(id, "Test Member");

        mockMvc.perform(as(delete("/api/communities/" + id), administrator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUNITY_NOT_EMPTY"));
    }

    @Test
    void deleteWithUsersIsRejected() throws Exception {
        UUID id = fixture.community(12);
        fixture.user(UserRole.REGISTERED, id);

        mockMvc.perform(as(delete("/api/communities/" + id), administrator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUNITY_NOT_EMPTY"));
    }

    @Test
    void deleteOfAnUnknownCommunityIsNotFound() throws Exception {
        mockMvc.perform(as(delete("/api/communities/" + UUID.randomUUID()), administrator))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivationWithPrivilegedUsersNeedsConfirmation() throws Exception {
        UUID id = fixture.community(12);
        fixture.user(UserRole.MODERATOR, id);

        mockMvc.perform(asJson(post("/api/communities/" + id + "/deactivation"), administrator,
                "{\"confirmed\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMUNITY_HAS_PRIVILEGED_USERS"));
        assertThat(jdbc.queryForObject("select status from community where id = ?", String.class, id))
                .isEqualTo("ACTIVE");
    }

    @Test
    void deactivationWithConfirmationReturns204AndUpdatesTheProjection() throws Exception {
        UUID id = fixture.community(12);
        fixture.user(UserRole.MODERATOR, id);

        mockMvc.perform(asJson(post("/api/communities/" + id + "/deactivation"), administrator,
                "{\"confirmed\":true}"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select status from community where id = ?", String.class, id))
                .isEqualTo("DEACTIVATED");
        assertThat(jdbc.queryForObject("select active from identity_community where id = ?", Boolean.class, id))
                .isFalse();
    }

    @Test
    void deactivationWithOnlyRegisteredUsersNeedsNoConfirmation() throws Exception {
        UUID id = fixture.community(12);
        fixture.user(UserRole.REGISTERED, id);

        mockMvc.perform(asJson(post("/api/communities/" + id + "/deactivation"), administrator,
                "{\"confirmed\":false}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void reactivationReturns204AndRestoresTheActiveState() throws Exception {
        String id = create(12, "");
        deactivate(id);

        mockMvc.perform(as(post("/api/communities/" + id + "/reactivation"), administrator))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select active from identity_community where id = ?::uuid", Boolean.class,
                id)).isTrue();
        mockMvc.perform(as(get("/api/communities"), administrator)).andExpect(jsonPath("$", hasSize(2)));
    }

    static Stream<Arguments> managementCalls() {
        UUID id = UUID.randomUUID();
        return Stream.of(
                Arguments.of("create", (Supplier<MockHttpServletRequestBuilder>) () -> post("/api/communities"),
                        "{\"number\":50}"),
                Arguments.of("update", (Supplier<MockHttpServletRequestBuilder>) () -> put("/api/communities/" + id),
                        "{\"number\":50}"),
                Arguments.of("delete",
                        (Supplier<MockHttpServletRequestBuilder>) () -> delete("/api/communities/" + id), null),
                Arguments.of("deactivate", (Supplier<MockHttpServletRequestBuilder>) () -> post(
                        "/api/communities/" + id + "/deactivation"), "{\"confirmed\":true}"),
                Arguments.of("reactivate", (Supplier<MockHttpServletRequestBuilder>) () -> post(
                        "/api/communities/" + id + "/reactivation"), null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementCalls")
    void moderatorsAndRegisteredUsersCannotManageCommunities(String operation,
            Supplier<MockHttpServletRequestBuilder> request, String body) throws Exception {
        for (String bearer : new String[] {moderator, registered}) {
            MockHttpServletRequestBuilder builder = request.get().header(HttpHeaders.AUTHORIZATION, bearer);
            if (body != null) {
                builder = builder.contentType(MediaType.APPLICATION_JSON).content(body);
            }
            mockMvc.perform(builder).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        assertThat(jdbc.queryForObject("select count(*) from community", Integer.class)).isEqualTo(1);
    }
}
