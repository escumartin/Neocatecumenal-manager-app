package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class RoomsControllerTest {

    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(300000);

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

    private Community community;
    private String administrator;
    private String moderator;
    private String registered;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate booking_status_change, booking, booking_request, room, identity_login_pair_throttle, "
                + "identity_login_ip_throttle, identity_session, identity_user, identity_community cascade");
        community = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(1)));
        administrator = bearerFor(UserRole.ADMINISTRATOR);
        moderator = bearerFor(UserRole.MODERATOR);
        registered = bearerFor(UserRole.REGISTERED);
    }

    private String bearerFor(UserRole role) {
        User user = users.save(TestUsers.newUser(new UserId(UUID.randomUUID()),
                new AccessId(ACCESS_IDS.incrementAndGet()), new DisplayName("Test Person"), role, community.id(),
                new PasswordHash("{argon2}hash"), false, new PrivacyConsent("v1", Instant.now())));
        IssuedSessionToken issued = tokens.issue();
        sessions.save(Session.open(user.id(), issued.hash(), Instant.now()));
        return "Bearer " + issued.token();
    }

    private static String nameJson(String name) {
        return "{\"name\":\"" + name + "\"}";
    }

    private MockHttpServletRequestBuilder asJson(MockHttpServletRequestBuilder builder, String bearer, String body) {
        return builder.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String createRoom(String name) throws Exception {
        String body = mockMvc.perform(asJson(post("/api/rooms"), administrator, nameJson(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void deactivateRoom(String id) throws Exception {
        mockMvc.perform(post("/api/rooms/" + id + "/deactivation").header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isNoContent());
    }

    private void bookingOccurrence(String roomId, String date, String start, String status) {
        UUID request = UUID.randomUUID();
        jdbc.update("insert into booking_request (id, room_id, community_id, reason, first_date, last_date, "
                + "start_time, end_time, status, version, created_at) "
                + "values (?, ?::uuid, ?, 'Meeting', ?::date, ?::date, ?::time, ?::time + interval '1 hour', ?, 0, "
                + "now())", request, roomId, community.id().value(), date, date, start, start, status);
        jdbc.update("insert into booking (id, booking_request_id, room_id, booking_date, start_time, end_time, "
                + "status) values (?, ?, ?::uuid, ?::date, ?::time, ?::time + interval '1 hour', ?)",
                UUID.randomUUID(), request, roomId, date, start, start, status);
    }

    @Test
    void administratorCreatesARoom() throws Exception {
        String body = mockMvc.perform(asJson(post("/api/rooms"), administrator, nameJson("Main hall")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/rooms/")))
                .andExpect(jsonPath("$.name").value("Main hall"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.id");
        assertThat(jdbc.queryForObject("select name from room where id = ?::uuid", String.class, id))
                .isEqualTo("Main hall");
    }

    @Test
    void createTrimsTheName() throws Exception {
        mockMvc.perform(asJson(post("/api/rooms"), administrator, nameJson("  Main hall  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Main hall"));
    }

    @Test
    void createWithAnEmptyOrBlankNameIsRequired() throws Exception {
        for (String name : new String[] {"", "   "}) {
            mockMvc.perform(asJson(post("/api/rooms"), administrator, nameJson(name)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("name"))
                    .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        }
        assertThat(jdbc.queryForObject("select count(*) from room", Integer.class)).isZero();
    }

    @Test
    void createWithoutANameIsRequired() throws Exception {
        mockMvc.perform(asJson(post("/api/rooms"), administrator, "{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void createWithANulCharacterInTheNameIsInvalidFormat() throws Exception {
        mockMvc.perform(asJson(post("/api/rooms"), administrator, nameJson("Main\\u0000hall")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
        assertThat(jdbc.queryForObject("select count(*) from room", Integer.class)).isZero();
    }

    @Test
    void createWithATooLongNameIsRejected() throws Exception {
        mockMvc.perform(asJson(post("/api/rooms"), administrator, nameJson("x".repeat(81))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
    }

    @Test
    void createWithARepeatedNameIgnoringCaseIsAConflict() throws Exception {
        createRoom("Main hall");

        mockMvc.perform(asJson(post("/api/rooms"), administrator, nameJson("MAIN HALL")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ROOM_NAME_TAKEN"));
        assertThat(jdbc.queryForObject("select count(*) from room", Integer.class)).isEqualTo(1);
    }

    @Test
    void createReusesTheNameOfADeactivatedRoom() throws Exception {
        deactivateRoom(createRoom("Main hall"));

        mockMvc.perform(asJson(post("/api/rooms"), administrator, nameJson("Main hall")))
                .andExpect(status().isCreated());
    }

    @Test
    void moderatorsAndRegisteredUsersCannotCreateRooms() throws Exception {
        mockMvc.perform(asJson(post("/api/rooms"), moderator, nameJson("Main hall")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(asJson(post("/api/rooms"), registered, nameJson("Main hall")))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from room", Integer.class)).isZero();
    }

    @Test
    void administratorRenamesARoom() throws Exception {
        String id = createRoom("Main hall");

        mockMvc.perform(asJson(put("/api/rooms/" + id), administrator, nameJson("Side hall")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Side hall"))
                .andExpect(jsonPath("$.active").value(true));
        assertThat(jdbc.queryForObject("select name from room where id = ?::uuid", String.class, id))
                .isEqualTo("Side hall");
    }

    @Test
    void renameToANameTakenByAnotherActiveRoomIsAConflict() throws Exception {
        String id = createRoom("Main hall");
        createRoom("Side hall");

        mockMvc.perform(asJson(put("/api/rooms/" + id), administrator, nameJson("side hall")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NAME_TAKEN"));
    }

    @Test
    void renameOfADeactivatedRoomIsAConflict() throws Exception {
        String id = createRoom("Main hall");
        deactivateRoom(id);

        mockMvc.perform(asJson(put("/api/rooms/" + id), administrator, nameJson("Side hall")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_INACTIVE"));
    }

    @Test
    void renameOfAnUnknownRoomIsNotFound() throws Exception {
        mockMvc.perform(asJson(put("/api/rooms/" + UUID.randomUUID()), administrator, nameJson("Side hall")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void renameWithAnEmptyNameIsRequired() throws Exception {
        String id = createRoom("Main hall");

        mockMvc.perform(asJson(put("/api/rooms/" + id), administrator, nameJson("")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void moderatorsAndRegisteredUsersCannotRename() throws Exception {
        String id = createRoom("Main hall");

        mockMvc.perform(asJson(put("/api/rooms/" + id), moderator, nameJson("Side hall")))
                .andExpect(status().isForbidden());
        mockMvc.perform(asJson(put("/api/rooms/" + id), registered, nameJson("Side hall")))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorDeactivatesARoomAndItDisappearsFromTheListButTheRowIsKept() throws Exception {
        String id = createRoom("Main hall");
        createRoom("Side hall");

        deactivateRoom(id);

        mockMvc.perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Side hall"));
        assertThat(jdbc.queryForObject("select active from room where id = ?::uuid", Boolean.class, id)).isFalse();
    }

    @Test
    void deactivateWithFutureActiveBookingsReportsTheCount() throws Exception {
        String id = createRoom("Main hall");
        bookingOccurrence(id, "2099-01-05", "10:00", "PENDING");
        bookingOccurrence(id, "2099-01-12", "10:00", "APPROVED");
        bookingOccurrence(id, "2099-01-19", "10:00", "REJECTED");

        mockMvc.perform(post("/api/rooms/" + id + "/deactivation").header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ROOM_HAS_ACTIVE_BOOKINGS"))
                .andExpect(jsonPath("$.activeBookings").value(2));
        assertThat(jdbc.queryForObject("select active from room where id = ?::uuid", Boolean.class, id)).isTrue();
    }

    @Test
    void deactivateIgnoresPastBookings() throws Exception {
        String id = createRoom("Main hall");
        bookingOccurrence(id, "2020-01-05", "10:00", "APPROVED");

        deactivateRoom(id);
    }

    @Test
    void deactivateOfADeactivatedRoomIsAConflict() throws Exception {
        String id = createRoom("Main hall");
        deactivateRoom(id);

        mockMvc.perform(post("/api/rooms/" + id + "/deactivation").header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_INACTIVE"));
    }

    @Test
    void deactivateOfAnUnknownRoomIsNotFound() throws Exception {
        mockMvc.perform(post("/api/rooms/" + UUID.randomUUID() + "/deactivation")
                        .header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void moderatorsAndRegisteredUsersCannotDeactivate() throws Exception {
        String id = createRoom("Main hall");

        mockMvc.perform(post("/api/rooms/" + id + "/deactivation").header(HttpHeaders.AUTHORIZATION, moderator))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/rooms/" + id + "/deactivation").header(HttpHeaders.AUTHORIZATION, registered))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select active from room where id = ?::uuid", Boolean.class, id)).isTrue();
    }

    @Test
    void everyRoleListsOnlyTheActiveRooms() throws Exception {
        String removed = createRoom("Old hall");
        createRoom("Main hall");
        deactivateRoom(removed);

        for (String bearer : new String[] {administrator, moderator, registered}) {
            mockMvc.perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name").value("Main hall"))
                    .andExpect(jsonPath("$[0].active").value(true));
        }
    }

    @Test
    void listWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/rooms")).andExpect(status().isUnauthorized());
    }

    @Test
    void createWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(nameJson("Main hall")))
                .andExpect(status().isUnauthorized());
    }
}
