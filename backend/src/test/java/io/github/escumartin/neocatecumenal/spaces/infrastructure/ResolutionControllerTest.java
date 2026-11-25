package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class ResolutionControllerTest {

    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(700000);

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
    private int createdRequests;

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
        createdRequests = 0;
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

    private String requestJson(String room, String communityId, String date, String start, String end,
            String reason) {
        String community = communityId == null ? "" : ",\"communityId\":\"" + communityId + "\"";
        return "{\"roomId\":\"" + room + "\",\"date\":\"" + date + "\",\"startTime\":\"" + start
                + "\",\"endTime\":\"" + end + "\",\"reason\":\"" + reason + "\"" + community + "}";
    }

    private String defaultRequest(String communityId) {
        return requestJson(roomId, communityId, tomorrow(), "18:00", "20:00", "Catechesis");
    }

    private String createRequest(String bearer, String communityId) throws Exception {
        int hour = 8 + createdRequests++;
        String request = requestJson(roomId, communityId, tomorrow(), String.format("%02d:00", hour),
                String.format("%02d:30", hour), "Catechesis");
        String body = mockMvc.perform(asJson(post("/api/booking-requests"), bearer, request))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void anAdministratorApprovesAPendingRequest() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(post("/api/booking-requests/" + id + "/approval").header(HttpHeaders.AUTHORIZATION,
                administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());

        assertThat(jdbc.queryForObject("select status from booking_request where id = ?::uuid", String.class, id))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select status from booking", String.class)).isEqualTo("APPROVED");
        assertThat(count("booking_status_change")).isEqualTo(2);
    }

    @Test
    void rejectingWithANulCharacterInTheReasonIsInvalidFormat() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(asJson(post("/api/booking-requests/" + id + "/rejection"), administrator,
                "{\"reason\":\"Hall\\u0000closed\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("reason"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void anAdministratorRejectsWithAReasonAndTheRequesterSeesIt() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(asJson(post("/api/booking-requests/" + id + "/rejection"), administrator,
                "{\"reason\":\"Hall under maintenance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Hall under maintenance"));

        mockMvc.perform(get("/api/booking-requests/" + id).header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Hall under maintenance"))
                .andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.history[1].toStatus").value("REJECTED"));
        mockMvc.perform(get("/api/booking-requests").header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(jsonPath("$.items[0].rejectionReason").value("Hall under maintenance"));
        assertThat(jdbc.queryForObject("select status from booking", String.class)).isEqualTo("REJECTED");
    }

    @Test
    void anAdministratorRejectsWithoutABody() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(post("/api/booking-requests/" + id + "/rejection").header(HttpHeaders.AUTHORIZATION,
                administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    @Test
    void anAdministratorRejectsWithAnEmptyObject() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(asJson(post("/api/booking-requests/" + id + "/rejection"), administrator, "{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void rejectingFreesTheSlotForANewRequest() throws Exception {
        String id = createRequest(moderatorA, null);
        mockMvc.perform(post("/api/booking-requests/" + id + "/rejection").header(HttpHeaders.AUTHORIZATION,
                administrator)).andExpect(status().isOk());

        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorB, requestJson(roomId, null, tomorrow(),
                "08:00", "08:30", "Catechesis"))).andExpect(status().isCreated());
    }

    @Test
    void aRejectionReasonOfFiveHundredCharactersIsAccepted() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(asJson(post("/api/booking-requests/" + id + "/rejection"), administrator,
                "{\"reason\":\"" + "x".repeat(500) + "\"}")).andExpect(status().isOk());
    }

    @Test
    void aRejectionReasonOfFiveHundredAndOneCharactersIsTooLong() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(asJson(post("/api/booking-requests/" + id + "/rejection"), administrator,
                "{\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("reason"))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
        assertThat(jdbc.queryForObject("select status from booking_request where id = ?::uuid", String.class, id))
                .isEqualTo("PENDING");
    }

    @Test
    void aBlankRejectionReasonIsRequired() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(asJson(post("/api/booking-requests/" + id + "/rejection"), administrator,
                "{\"reason\":\"   \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("reason"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void moderatorsAndRegisteredUsersCannotResolve() throws Exception {
        String id = createRequest(moderatorA, null);

        for (String bearer : new String[] {moderatorA, moderatorB, registered}) {
            mockMvc.perform(post("/api/booking-requests/" + id + "/approval").header(HttpHeaders.AUTHORIZATION,
                    bearer)).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/booking-requests/" + id + "/rejection").header(HttpHeaders.AUTHORIZATION,
                    bearer)).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("select status from booking_request where id = ?::uuid", String.class, id))
                .isEqualTo("PENDING");
    }

    @Test
    void resolvingWithoutAuthenticationIsUnauthorized() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(post("/api/booking-requests/" + id + "/approval")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/booking-requests/" + id + "/rejection")).andExpect(status().isUnauthorized());
    }

    @Test
    void resolvingAnUnknownRequestIsNotFound() throws Exception {
        mockMvc.perform(post("/api/booking-requests/" + UUID.randomUUID() + "/approval")
                .header(HttpHeaders.AUTHORIZATION, administrator)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/booking-requests/" + UUID.randomUUID() + "/rejection")
                .header(HttpHeaders.AUTHORIZATION, administrator)).andExpect(status().isNotFound());
    }

    @Test
    void resolvingTwiceIsAConflict() throws Exception {
        String id = createRequest(moderatorA, null);
        mockMvc.perform(post("/api/booking-requests/" + id + "/approval").header(HttpHeaders.AUTHORIZATION,
                administrator)).andExpect(status().isOk());

        mockMvc.perform(post("/api/booking-requests/" + id + "/approval").header(HttpHeaders.AUTHORIZATION,
                administrator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_REQUEST_ALREADY_RESOLVED"));
        mockMvc.perform(post("/api/booking-requests/" + id + "/rejection").header(HttpHeaders.AUTHORIZATION,
                administrator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_REQUEST_ALREADY_RESOLVED"));
    }

    @Test
    void approvingARejectedOrCancelledRequestIsAConflict() throws Exception {
        String rejected = createRequest(moderatorA, null);
        String cancelled = createRequest(moderatorA, null);
        mockMvc.perform(post("/api/booking-requests/" + rejected + "/rejection")
                .header(HttpHeaders.AUTHORIZATION, administrator)).andExpect(status().isOk());
        jdbc.update("update booking_request set status = 'CANCELLED' where id = ?::uuid", cancelled);

        for (String id : new String[] {rejected, cancelled}) {
            mockMvc.perform(post("/api/booking-requests/" + id + "/approval").header(HttpHeaders.AUTHORIZATION,
                    administrator))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("BOOKING_REQUEST_ALREADY_RESOLVED"));
        }
    }

    @Test
    void approvingARequestWhoseFirstOccurrenceStartedIsUnprocessableButRejectingIsAllowed() throws Exception {
        String started = createRequest(moderatorA, null);
        String startedToReject = createRequest(moderatorA, null);
        jdbc.update("update booking_request set first_date = current_date - 1, last_date = current_date - 1");
        jdbc.update("update booking set booking_date = current_date - 1");

        mockMvc.perform(post("/api/booking-requests/" + started + "/approval").header(HttpHeaders.AUTHORIZATION,
                administrator))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("START_NOT_IN_FUTURE"));
        mockMvc.perform(post("/api/booking-requests/" + startedToReject + "/rejection")
                .header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void theAdministratorListsThePendingRequestsOfEveryCommunity() throws Exception {
        String ofA = createRequest(moderatorA, null);
        String ofB = createRequest(moderatorB, null);
        String resolved = createRequest(moderatorA, null);
        mockMvc.perform(post("/api/booking-requests/" + resolved + "/approval")
                .header(HttpHeaders.AUTHORIZATION, administrator)).andExpect(status().isOk());

        mockMvc.perform(get("/api/booking-requests?status=PENDING").header(HttpHeaders.AUTHORIZATION,
                administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(ofA, ofB)));
    }
}
