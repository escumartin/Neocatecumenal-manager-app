package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
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
class BookingResolutionIT {

    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(800000);

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
    private String otherAdministrator;
    private String moderatorA;
    private String moderatorB;
    private String roomId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("truncate booking_status_change, booking, booking_request, room, identity_login_pair_throttle, "
                + "identity_login_ip_throttle, identity_session, identity_user, identity_community cascade");
        communityA = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(1)));
        communityB = communities.save(new Community(new CommunityId(UUID.randomUUID()), new CommunityNumber(2)));
        administrator = bearerFor(UserRole.ADMINISTRATOR, communityA);
        otherAdministrator = bearerFor(UserRole.ADMINISTRATOR, communityB);
        moderatorA = bearerFor(UserRole.MODERATOR, communityA);
        moderatorB = bearerFor(UserRole.MODERATOR, communityB);
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

    private String pendingRequest() throws Exception {
        String body = "{\"roomId\":\"" + roomId + "\",\"date\":\"" + tomorrow()
                + "\",\"startTime\":\"18:00\",\"endTime\":\"20:00\",\"reason\":\"Catechesis\"}";
        String response = mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA, body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String pendingSeries(int weeks) throws Exception {
        LocalDate first = LocalDate.parse(tomorrow());
        String body = "{\"roomId\":\"" + roomId + "\",\"date\":\"" + first
                + "\",\"startTime\":\"18:00\",\"endTime\":\"20:00\",\"reason\":\"Catechesis\","
                + "\"recurrence\":{\"frequency\":\"WEEKLY\",\"until\":\"" + first.plusWeeks(weeks - 1) + "\"}}";
        String response = mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA, body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private int occurrencesWithStatus(String status) {
        return jdbc.queryForObject("select count(*) from booking where status = ?", Integer.class, status);
    }

    private int resolve(String bearer, String id, String action) throws Exception {
        return mockMvc.perform(post("/api/booking-requests/" + id + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, bearer)).andReturn().getResponse().getStatus();
    }

    private List<Integer> resolveSimultaneously(String id, String firstAction, String secondAction)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (var attempt : List.of(Map.entry(administrator, firstAction),
                    Map.entry(otherAdministrator, secondAction))) {
                results.add(executor.submit(() -> {
                    start.await();
                    return resolve(attempt.getKey(), id, attempt.getValue());
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    private int historyRows() {
        return jdbc.queryForObject("select count(*) from booking_status_change", Integer.class);
    }

    @Test
    void twoAdministratorsApprovingTheSameRequestLeaveOneSuccessAndOneConflict() throws Exception {
        String id = pendingRequest();

        List<Integer> statuses = resolveSimultaneously(id, "approval", "approval");

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select status from booking_request", String.class)).isEqualTo("APPROVED");
        assertThat(historyRows()).isEqualTo(2);
    }

    @Test
    void anApprovalAndARejectionAtTheSameTimeLeaveASingleResolution() throws Exception {
        String id = pendingRequest();

        List<Integer> statuses = resolveSimultaneously(id, "approval", "rejection");

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(historyRows()).isEqualTo(2);
        String requestStatus = jdbc.queryForObject("select status from booking_request", String.class);
        assertThat(jdbc.queryForObject("select status from booking", String.class)).isEqualTo(requestStatus);
    }

    @Test
    void rejectingStoresTheReasonAndReleasesTheSlot() throws Exception {
        String id = pendingRequest();

        mockMvc.perform(asJson(post("/api/booking-requests/" + id + "/rejection"), administrator,
                "{\"reason\":\"Hall under maintenance\"}")).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select rejection_reason from booking_request", String.class))
                .isEqualTo("Hall under maintenance");
        assertThat(jdbc.queryForObject("select count(*) from booking where status in ('PENDING', 'APPROVED')",
                Integer.class)).isZero();
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorB, "{\"roomId\":\"" + roomId
                + "\",\"date\":\"" + tomorrow()
                + "\",\"startTime\":\"18:00\",\"endTime\":\"20:00\",\"reason\":\"Rehearsal\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void approvingKeepsTheSlotOccupiedForOthers() throws Exception {
        String id = pendingRequest();
        assertThat(resolve(administrator, id, "approval")).isEqualTo(200);

        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorB, "{\"roomId\":\"" + roomId
                + "\",\"date\":\"" + tomorrow()
                + "\",\"startTime\":\"18:00\",\"endTime\":\"20:00\",\"reason\":\"Rehearsal\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void approvingASeriesLeavesEveryOccurrenceApproved() throws Exception {
        String id = pendingSeries(6);

        assertThat(resolve(administrator, id, "approval")).isEqualTo(200);

        assertThat(occurrencesWithStatus("APPROVED")).isEqualTo(6);
        assertThat(jdbc.queryForObject("select status from booking_request", String.class)).isEqualTo("APPROVED");
    }

    @Test
    void aPendingSeriesAlreadyBlocksItsSlotsSoApprovingItNeverCollides() throws Exception {
        pendingSeries(4);

        assertThatThrownBy(() -> jdbc.update("insert into booking (id, booking_request_id, room_id, booking_date, "
                + "start_time, end_time, status) select gen_random_uuid(), booking_request_id, room_id, "
                + "booking_date, '19:00', '21:00', 'APPROVED' from booking limit 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(occurrencesWithStatus("PENDING")).isEqualTo(4);
    }

    @Test
    void twoAdministratorsApprovingTheSameSeriesLeaveOneSuccessAndOneConflict() throws Exception {
        String id = pendingSeries(5);

        List<Integer> statuses = resolveSimultaneously(id, "approval", "approval");

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(occurrencesWithStatus("APPROVED")).isEqualTo(5);
    }

    @Test
    void approvingASeriesWhoseFirstOccurrenceHasStartedIsRejectedButRejectingItIsAllowed() throws Exception {
        String id = pendingSeries(4);
        jdbc.update("update booking set booking_date = date '2000-01-03' where booking_date = "
                + "(select min(booking_date) from booking)");
        jdbc.update("update booking_request set first_date = date '2000-01-03'");

        mockMvc.perform(post("/api/booking-requests/" + id + "/approval").header(HttpHeaders.AUTHORIZATION,
                administrator))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("START_NOT_IN_FUTURE"));
        assertThat(occurrencesWithStatus("PENDING")).isEqualTo(4);

        assertThat(resolve(administrator, id, "rejection")).isEqualTo(200);
        assertThat(occurrencesWithStatus("REJECTED")).isEqualTo(4);
    }

    @Test
    void rejectingASeriesReleasesEveryOccurrenceSlot() throws Exception {
        String id = pendingSeries(4);

        assertThat(resolve(administrator, id, "rejection")).isEqualTo(200);

        assertThat(occurrencesWithStatus("REJECTED")).isEqualTo(4);
        pendingSeries(4);
    }
}
