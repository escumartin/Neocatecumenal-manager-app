package io.github.escumartin.neocatecumenal.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
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
class EventsControllerTest {

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
        jdbc.execute("truncate event, booking_status_change, booking, booking_request, room, "
                + "identity_login_pair_throttle, identity_login_ip_throttle, identity_session, identity_user, "
                + "identity_community cascade");
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

    private String eventJson(String type, String title, String room, String communityId, String start, String end) {
        String community = communityId == null ? "" : ",\"communityId\":\"" + communityId + "\"";
        return "{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"roomId\":\"" + room + "\",\"date\":\""
                + tomorrow() + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"" + community + "}";
    }

    private String defaultEvent(String communityId) {
        return eventJson("WORD", "Opening word", roomId, communityId, "18:00", "20:00");
    }

    private String createEvent(String bearer, String communityId) throws Exception {
        String body = mockMvc.perform(asJson(post("/api/events"), bearer, defaultEvent(communityId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void aModeratorCreatesAnEventForTheirCommunity() throws Exception {
        String body = mockMvc.perform(asJson(post("/api/events"), moderatorA, defaultEvent(null)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/events/")))
                .andExpect(jsonPath("$.type").value("WORD"))
                .andExpect(jsonPath("$.title").value("Opening word"))
                .andExpect(jsonPath("$.communityId").value(communityA.id().value().toString()))
                .andExpect(jsonPath("$.roomId").value(roomId))
                .andExpect(jsonPath("$.date").value(tomorrow()))
                .andExpect(jsonPath("$.startTime").value("18:00"))
                .andExpect(jsonPath("$.endTime").value("20:00"))
                .andExpect(jsonPath("$.bookingStatus").value("PENDING"))
                .andExpect(jsonPath("$.bookingRequestId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String requestId = JsonPath.read(body, "$.bookingRequestId");
        String eventId = JsonPath.read(body, "$.id");
        assertThat(count("event")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from booking_request where id = ?::uuid", String.class,
                requestId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select event_id from booking_request where id = ?::uuid", UUID.class,
                requestId)).isEqualTo(UUID.fromString(eventId));
    }

    @Test
    void anAdministratorCreatesAnEventForTheChosenCommunity() throws Exception {
        mockMvc.perform(asJson(post("/api/events"), administrator,
                eventJson("CONVIVENCE", "Day trip", roomId, communityB.id().value().toString(), "09:00", "18:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("CONVIVENCE"))
                .andExpect(jsonPath("$.communityId").value(communityB.id().value().toString()));
        assertThat(jdbc.queryForObject("select community_id from booking_request", UUID.class))
                .isEqualTo(communityB.id().value());
    }

    @Test
    void anAdministratorWithoutACommunityIsRequiredToChooseOne() throws Exception {
        mockMvc.perform(asJson(post("/api/events"), administrator, defaultEvent(null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("communityId"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_REQUIRED"));
        assertThat(count("event")).isZero();
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void anAdministratorChoosingAnUnknownCommunityIsRejected() throws Exception {
        mockMvc.perform(asJson(post("/api/events"), administrator, defaultEvent(UUID.randomUUID().toString())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_NOT_FOUND"));
        assertThat(count("event")).isZero();
    }

    @Test
    void aModeratorCannotCreateAnEventForAnotherCommunity() throws Exception {
        mockMvc.perform(asJson(post("/api/events"), moderatorA, defaultEvent(communityB.id().value().toString())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(count("event")).isZero();
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void aRegisteredUserCannotCreateAnEvent() throws Exception {
        mockMvc.perform(asJson(post("/api/events"), registered, defaultEvent(null)))
                .andExpect(status().isForbidden());
        assertThat(count("event")).isZero();
    }

    @Test
    void anUnsupportedTypeIsInvalidFormat() throws Exception {
        mockMvc.perform(asJson(post("/api/events"), moderatorA,
                eventJson("PARTY", "Opening word", roomId, null, "18:00", "20:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
        assertThat(count("event")).isZero();
    }

    @Test
    void aTitleOfOneHundredAndTwentyOneCharactersIsTooLong() throws Exception {
        mockMvc.perform(asJson(post("/api/events"), moderatorA,
                eventJson("WORD", "x".repeat(121), roomId, null, "18:00", "20:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
        assertThat(count("event")).isZero();
    }

    @Test
    void aTitleWithANulCharacterIsInvalidFormat() throws Exception {
        mockMvc.perform(asJson(post("/api/events"), moderatorA,
                eventJson("WORD", "Open\\u0000ing", roomId, null, "18:00", "20:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
        assertThat(count("event")).isZero();
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void aBlankTitleIsRequired() throws Exception {
        mockMvc.perform(asJson(post("/api/events"), moderatorA,
                eventJson("WORD", "   ", roomId, null, "18:00", "20:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void anEndNotAfterTheStartIsRejected() throws Exception {
        mockMvc.perform(asJson(post("/api/events"), moderatorA,
                eventJson("WORD", "Opening word", roomId, null, "20:00", "18:00")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("endTime"))
                .andExpect(jsonPath("$.errors[0].code").value("END_NOT_AFTER_START"));
    }

    @Test
    void aStartInThePastIsRejected() throws Exception {
        String yesterday = LocalDate.now(ZoneId.of("Europe/Madrid")).minusDays(1).toString();
        mockMvc.perform(asJson(post("/api/events"), moderatorA,
                "{\"type\":\"WORD\",\"title\":\"Opening word\",\"roomId\":\"" + roomId + "\",\"date\":\""
                        + yesterday + "\",\"startTime\":\"18:00\",\"endTime\":\"20:00\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("START_NOT_IN_FUTURE"));
        assertThat(count("event")).isZero();
    }

    @Test
    void anOccupiedRoomIsAConflictWithItsDatesAndSavesNothingNew() throws Exception {
        createEvent(moderatorA, null);

        mockMvc.perform(asJson(post("/api/events"), moderatorB,
                eventJson("WORD", "Second word", roomId, null, "19:00", "21:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_OCCUPIED"))
                .andExpect(jsonPath("$.conflicts", hasSize(1)))
                .andExpect(jsonPath("$.conflicts[0].date").value(tomorrow()))
                .andExpect(jsonPath("$.conflicts[0].startTime").value("18:00"))
                .andExpect(jsonPath("$.conflicts[0].endTime").value("20:00"));
        assertThat(count("event")).isEqualTo(1);
        assertThat(count("booking_request")).isEqualTo(1);
    }

    @Test
    void anInactiveRoomIsAConflict() throws Exception {
        mockMvc.perform(post("/api/rooms/" + roomId + "/deactivation").header(HttpHeaders.AUTHORIZATION,
                administrator)).andExpect(status().isNoContent());

        mockMvc.perform(asJson(post("/api/events"), moderatorA, defaultEvent(null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_INACTIVE"));
        assertThat(count("event")).isZero();
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void anUnknownRoomIsNotFound() throws Exception {
        mockMvc.perform(asJson(post("/api/events"), moderatorA,
                eventJson("WORD", "Opening word", UUID.randomUUID().toString(), null, "18:00", "20:00")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(count("event")).isZero();
    }

    @Test
    void creatingWithoutASessionIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(defaultEvent(null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aModeratorGetsAnEventOfTheirCommunityWithItsBookingStatus() throws Exception {
        String id = createEvent(moderatorA, null);

        mockMvc.perform(get("/api/events/" + id).header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Opening word"))
                .andExpect(jsonPath("$.bookingStatus").value("PENDING"));
    }

    @Test
    void theBookingStatusFollowsTheRequestAfterItIsRejected() throws Exception {
        String id = createEvent(moderatorA, null);
        String requestId = jdbc.queryForObject("select booking_request_id from event", UUID.class).toString();
        mockMvc.perform(asJson(post("/api/booking-requests/" + requestId + "/rejection"), administrator, "{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/events/" + id).header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("REJECTED"));
    }

    @Test
    void anAdministratorGetsAnEventOfAnyCommunity() throws Exception {
        String id = createEvent(moderatorB, null);

        mockMvc.perform(get("/api/events/" + id).header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communityId").value(communityB.id().value().toString()));
    }

    @Test
    void aModeratorGetsNotFoundForAnEventOfAnotherCommunity() throws Exception {
        String id = createEvent(moderatorB, null);

        mockMvc.perform(get("/api/events/" + id).header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnknownEventIsNotFound() throws Exception {
        mockMvc.perform(get("/api/events/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void anEventWhoseBookingRequestWasPurgedIsNotFound() throws Exception {
        String id = createEvent(moderatorA, null);
        jdbc.execute("truncate booking_status_change, booking, booking_request cascade");

        mockMvc.perform(get("/api/events/" + id).header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isNotFound());
    }
}
