package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
class CancellationControllerTest {

    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(900000);

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

    private String approvedRequest(String bearer) throws Exception {
        String id = createRequest(bearer, null);
        mockMvc.perform(post("/api/booking-requests/" + id + "/approval").header(HttpHeaders.AUTHORIZATION,
                administrator)).andExpect(status().isOk());
        return id;
    }

    private String onlyBookingOf(String requestId) {
        return jdbc.queryForObject("select id::text from booking where booking_request_id = ?::uuid", String.class,
                requestId);
    }

    private void moveToThePast(String requestId) {
        jdbc.update("update booking set booking_date = current_date - 1 where booking_request_id = ?::uuid",
                requestId);
        jdbc.update("update booking_request set first_date = current_date - 1, last_date = current_date - 1 "
                + "where id = ?::uuid", requestId);
    }

    private String addPastOccurrence(String requestId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("insert into booking (id, booking_request_id, room_id, booking_date, start_time, end_time, "
                + "status) select ?::uuid, id, room_id, current_date - 7, start_time, end_time, 'APPROVED' "
                + "from booking_request where id = ?::uuid", id, requestId);
        return id;
    }

    private MockHttpServletRequestBuilder cancel(String requestId, String bearer) {
        return post("/api/booking-requests/" + requestId + "/cancellation").header(HttpHeaders.AUTHORIZATION, bearer);
    }

    private MockHttpServletRequestBuilder cancelOccurrence(String requestId, String bookingId, String bearer) {
        return post("/api/booking-requests/" + requestId + "/bookings/" + bookingId + "/cancellation")
                .header(HttpHeaders.AUTHORIZATION, bearer);
    }

    @Test
    void aModeratorCancelsAnApprovedRequestOfTheirCommunity() throws Exception {
        String id = approvedRequest(moderatorA);

        mockMvc.perform(cancel(id, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(jdbc.queryForObject("select status from booking", String.class)).isEqualTo("CANCELLED");
        assertThat(count("booking_status_change")).isEqualTo(3);
    }

    @Test
    void anAdministratorCancelsARequestOfAnotherCommunity() throws Exception {
        String id = approvedRequest(moderatorB);

        mockMvc.perform(cancel(id, administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void aPendingRequestCanBeCancelled() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(cancel(id, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void aModeratorCannotCancelARequestOfAnotherCommunity() throws Exception {
        String id = approvedRequest(moderatorB);

        mockMvc.perform(cancel(id, moderatorA)).andExpect(status().isForbidden());
        mockMvc.perform(cancel(id, registered)).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select status from booking", String.class)).isEqualTo("APPROVED");
    }

    @Test
    void cancellingARequestWhoseSlotStartedIsAConflict() throws Exception {
        String id = approvedRequest(moderatorA);
        moveToThePast(id);

        mockMvc.perform(cancel(id, moderatorA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_ALREADY_STARTED"));
    }

    @Test
    void cancellingASeriesWithoutFutureOccurrencesIsAConflict() throws Exception {
        String id = approvedRequest(moderatorA);
        moveToThePast(id);
        addPastOccurrence(id);

        mockMvc.perform(cancel(id, moderatorA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_ALREADY_STARTED"));
    }

    @Test
    void cancellingARejectedRequestIsAConflict() throws Exception {
        String id = createRequest(moderatorA, null);
        mockMvc.perform(post("/api/booking-requests/" + id + "/rejection").header(HttpHeaders.AUTHORIZATION,
                administrator)).andExpect(status().isOk());

        mockMvc.perform(cancel(id, moderatorA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_REQUEST_NOT_ACTIVE"));
    }

    @Test
    void cancellingAnUnknownRequestIsNotFoundAndAnonymousIsUnauthorized() throws Exception {
        String unknown = UUID.randomUUID().toString();

        mockMvc.perform(cancel(unknown, administrator)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/booking-requests/" + unknown + "/cancellation"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/booking-requests/" + unknown + "/bookings/" + unknown + "/cancellation"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancellingASingleOccurrenceReturnsTheDetailWithItsHistory() throws Exception {
        String id = approvedRequest(moderatorA);
        String pastOccurrence = addPastOccurrence(id);
        String futureOccurrence = onlyFutureBookingOf(id);

        mockMvc.perform(cancelOccurrence(id, futureOccurrence, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.bookings", hasSize(2)))
                .andExpect(jsonPath("$.history[2].bookingId").value(futureOccurrence))
                .andExpect(jsonPath("$.history[2].toStatus").value("CANCELLED"));

        assertThat(jdbc.queryForObject("select status from booking where id = ?::uuid", String.class,
                pastOccurrence)).isEqualTo("APPROVED");
    }

    @Test
    void cancellingTheLastActiveOccurrenceCancelsTheRequest() throws Exception {
        String id = approvedRequest(moderatorA);

        mockMvc.perform(cancelOccurrence(id, onlyBookingOf(id), administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.history", hasSize(4)));
    }

    @Test
    void cancellingAnOccurrenceThatStartedIsAConflict() throws Exception {
        String id = approvedRequest(moderatorA);
        moveToThePast(id);

        mockMvc.perform(cancelOccurrence(id, onlyBookingOf(id), moderatorA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_ALREADY_STARTED"));
    }

    @Test
    void cancellingAnUnknownOccurrenceIsNotFound() throws Exception {
        String id = approvedRequest(moderatorA);

        mockMvc.perform(cancelOccurrence(id, UUID.randomUUID().toString(), moderatorA))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancellingAnOccurrenceOfAnotherCommunityIsForbidden() throws Exception {
        String id = approvedRequest(moderatorB);

        mockMvc.perform(cancelOccurrence(id, onlyBookingOf(id), moderatorA)).andExpect(status().isForbidden());
    }

    private String onlyFutureBookingOf(String requestId) {
        return jdbc.queryForObject("select id::text from booking where booking_request_id = ?::uuid "
                + "and booking_date > current_date - 1", String.class, requestId);
    }
}
