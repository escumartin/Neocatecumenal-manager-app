package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import io.github.escumartin.neocatecumenal.identity.TestCommunityProvisionerConfig;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
@Import(TestCommunityProvisionerConfig.class)
class RoomOccupancyControllerTest {

    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(500000);

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

    private Community communityA;
    private Community communityB;
    private String administrator;
    private String moderatorA;
    private String moderatorB;
    private String registered;
    private String roomId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("truncate booking_status_change, booking, booking_request, room, identity_login_pair_throttle, "
                + "identity_login_ip_throttle, identity_session, identity_user, identity_community cascade");
        communityA = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(1)));
        communityB = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(2)));
        administrator = bearerFor(UserRole.ADMINISTRATOR, communityA);
        moderatorA = bearerFor(UserRole.MODERATOR, communityA);
        moderatorB = bearerFor(UserRole.MODERATOR, communityB);
        registered = bearerFor(UserRole.REGISTERED, communityA);
        roomId = createRoom("Main hall");
    }

    private String bearerFor(UserRole role, Community community) {
        User user = users.save(TestUsers.newUser(new UserId(UUID.randomUUID()),
                new AccessId(ACCESS_IDS.incrementAndGet()), new DisplayName("Test Person"), role, community.id(),
                new PasswordHash("{argon2}hash"), false, new PrivacyConsent("v1", Instant.now())));
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(user.id(), issued.hash(), Instant.now()));
        return "Bearer " + issued.token();
    }

    private String createRoom(String name) throws Exception {
        String body = mockMvc.perform(asJson(post("/api/rooms"), administrator, "{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private MockHttpServletRequestBuilder asJson(MockHttpServletRequestBuilder builder, String bearer, String body) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String tomorrow() {
        return LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(1).toString();
    }

    private String requestJson(String room, String date, String start, String end) {
        return "{\"roomId\":\"" + room + "\",\"date\":\"" + date + "\",\"startTime\":\"" + start
                + "\",\"endTime\":\"" + end + "\",\"reason\":\"Private reason\"}";
    }

    @Test
    void listsTheOccupiedSlotsOrderedByStartWithoutReasonsOrPeople() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA, requestJson(roomId, tomorrow(), "18:00", "20:00")))
                .andExpect(status().isCreated());
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorB, requestJson(roomId, tomorrow(), "09:00", "10:00")))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/rooms/" + roomId + "/occupancy").param("date", tomorrow())
                .header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId))
                .andExpect(jsonPath("$.date").value(tomorrow()))
                .andExpect(jsonPath("$.occupied", hasSize(2)))
                .andExpect(jsonPath("$.occupied[0].startTime").value("09:00"))
                .andExpect(jsonPath("$.occupied[0].endTime").value("10:00"))
                .andExpect(jsonPath("$.occupied[0].status").value("PENDING"))
                .andExpect(jsonPath("$.occupied[0].ownCommunity").value(false))
                .andExpect(jsonPath("$.occupied[1].startTime").value("18:00"))
                .andExpect(jsonPath("$.occupied[1].ownCommunity").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("reason", "Private reason", "requestedBy", "communityId");
    }

    @Test
    void anEmptyDayHasNoOccupiedSlots() throws Exception {
        mockMvc.perform(get("/api/rooms/" + roomId + "/occupancy").param("date", tomorrow())
                .header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupied", hasSize(0)));
    }

    @Test
    void anAdministratorGetsOwnCommunityRelativeToTheChosenCommunity() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorB, requestJson(roomId, tomorrow(), "09:00", "10:00")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/rooms/" + roomId + "/occupancy").param("date", tomorrow())
                .param("communityId", communityB.id().value().toString())
                .header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occupied[0].ownCommunity").value(true));
    }

    @Test
    void anAdministratorWithoutACommunityMustChooseOne() throws Exception {
        mockMvc.perform(get("/api/rooms/" + roomId + "/occupancy").param("date", tomorrow())
                .header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("communityId"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_REQUIRED"));
    }

    @Test
    void aModeratorCannotAskForAnotherCommunity() throws Exception {
        mockMvc.perform(get("/api/rooms/" + roomId + "/occupancy").param("date", tomorrow())
                .param("communityId", communityB.id().value().toString())
                .header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isForbidden());
    }

    @Test
    void aRegisteredUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/rooms/" + roomId + "/occupancy").param("date", tomorrow())
                .header(HttpHeaders.AUTHORIZATION, registered))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnauthenticatedCallerIsRejected() throws Exception {
        mockMvc.perform(get("/api/rooms/" + roomId + "/occupancy").param("date", tomorrow()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aDeactivatedRoomIsNotFound() throws Exception {
        mockMvc.perform(post("/api/rooms/" + roomId + "/deactivation").header(HttpHeaders.AUTHORIZATION,
                administrator)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rooms/" + roomId + "/occupancy").param("date", tomorrow())
                .header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void anUnknownRoomIsNotFound() throws Exception {
        mockMvc.perform(get("/api/rooms/" + UUID.randomUUID() + "/occupancy").param("date", tomorrow())
                .header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMissingDateIsARequiredFieldValidationFailure() throws Exception {
        mockMvc.perform(get("/api/rooms/" + roomId + "/occupancy").header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("date"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }
}
