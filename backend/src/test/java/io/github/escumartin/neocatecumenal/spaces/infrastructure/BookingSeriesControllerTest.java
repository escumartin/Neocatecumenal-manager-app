package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class BookingSeriesControllerTest {

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

    private static LocalDate firstDate() {
        return LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(2);
    }

    private String seriesJson(String start, String end, String recurrence) {
        return "{\"roomId\":\"" + roomId + "\",\"date\":\"" + firstDate() + "\",\"startTime\":\"" + start
                + "\",\"endTime\":\"" + end + "\",\"reason\":\"Catechesis\",\"recurrence\":" + recurrence + "}";
    }

    private static String weekly(LocalDate until) {
        return "{\"frequency\":\"WEEKLY\",\"until\":\"" + until + "\"}";
    }

    private String createSeries(String bearer, int weeks) throws Exception {
        String body = mockMvc.perform(asJson(post("/api/booking-requests"), bearer,
                seriesJson("18:00", "20:00", weekly(firstDate().plusWeeks(weeks - 1)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private int count(String sql) {
        return jdbc.queryForObject("select count(*) from " + sql, Integer.class);
    }

    @Test
    void aSeriesOfEightWeeksIsCreatedWithEightOccurrences() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                seriesJson("18:00", "20:00", weekly(firstDate().plusWeeks(7)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.occurrenceCount").value(8))
                .andExpect(jsonPath("$.date").value(firstDate().toString()))
                .andExpect(jsonPath("$.recurrence.frequency").value("WEEKLY"))
                .andExpect(jsonPath("$.recurrence.until").value(firstDate().plusWeeks(7).toString()));

        assertThat(count("booking")).isEqualTo(8);
        assertThat(count("booking_request")).isEqualTo(1);
    }

    @Test
    void theDetailOfASeriesListsEveryOccurrenceWeekByWeek() throws Exception {
        String id = createSeries(moderatorA, 3);

        mockMvc.perform(get("/api/booking-requests/" + id).header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings", hasSize(3)))
                .andExpect(jsonPath("$.bookings[1].date").value(firstDate().plusWeeks(1).toString()))
                .andExpect(jsonPath("$.recurrence.until").value(firstDate().plusWeeks(2).toString()));
    }

    @Test
    void aConflictingOccurrenceRejectsTheWholeSeriesAndListsItsDate() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorB, "{\"roomId\":\"" + roomId
                + "\",\"date\":\"" + firstDate().plusWeeks(3) + "\",\"startTime\":\"19:00\",\"endTime\":\"21:00\","
                + "\"reason\":\"Rehearsal\"}")).andExpect(status().isCreated());

        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                seriesJson("18:00", "20:00", weekly(firstDate().plusWeeks(7)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_OCCUPIED"))
                .andExpect(jsonPath("$.conflicts", hasSize(1)))
                .andExpect(jsonPath("$.conflicts[0].date").value(firstDate().plusWeeks(3).toString()))
                .andExpect(jsonPath("$.conflicts[0].startTime").value("19:00"));

        assertThat(count("booking_request")).isEqualTo(1);
        assertThat(count("booking")).isEqualTo(1);
    }

    @Test
    void aSeriesWithoutAnEndDateIsRequired() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                seriesJson("18:00", "20:00", "{\"frequency\":\"WEEKLY\"}")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("recurrence.until"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void anEndDateMoreThanAYearAwayIsTooLong() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                seriesJson("18:00", "20:00", weekly(firstDate().plusYears(1).plusDays(1)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("recurrence.until"))
                .andExpect(jsonPath("$.errors[0].code").value("RECURRENCE_TOO_LONG"));
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void anEndDateExactlyOneYearAwayCreatesFiftyThreeOccurrences() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                seriesJson("18:00", "20:00", weekly(firstDate().plusYears(1)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.occurrenceCount").value(53));
    }

    @Test
    void anEndDateBeforeTheFirstDateIsRejected() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                seriesJson("18:00", "20:00", weekly(firstDate().minusDays(1)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("recurrence.until"))
                .andExpect(jsonPath("$.errors[0].code").value("RECURRENCE_UNTIL_BEFORE_DATE"));
    }

    @Test
    void aFrequencyOtherThanWeeklyIsInvalidFormat() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                seriesJson("18:00", "20:00", "{\"frequency\":\"DAILY\",\"until\":\"" + firstDate().plusWeeks(2)
                        + "\"}")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void anAdministratorApprovesTheWholeSeries() throws Exception {
        String id = createSeries(moderatorA, 4);

        mockMvc.perform(post("/api/booking-requests/" + id + "/approval")
                .header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(count("booking where status = 'APPROVED'")).isEqualTo(4);
    }

    @Test
    void anAdministratorRejectsTheWholeSeriesAndFreesItsSlots() throws Exception {
        String id = createSeries(moderatorA, 4);

        mockMvc.perform(asJson(post("/api/booking-requests/" + id + "/rejection"), administrator,
                "{\"reason\":\"Not available\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(count("booking where status = 'REJECTED'")).isEqualTo(4);
        createSeries(moderatorB, 4);
    }

    @Test
    void cancellingASeriesCancelsEveryFutureOccurrence() throws Exception {
        String id = createSeries(moderatorA, 4);

        mockMvc.perform(post("/api/booking-requests/" + id + "/cancellation")
                .header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(count("booking where status = 'CANCELLED'")).isEqualTo(4);
        createSeries(moderatorB, 4);
    }

    @Test
    void cancellingASeriesKeepsTheOccurrencesAlreadyStarted() throws Exception {
        String id = createSeries(moderatorA, 4);
        jdbc.update("update booking set booking_date = booking_date - 14 where booking_date = ?", firstDate());

        mockMvc.perform(post("/api/booking-requests/" + id + "/cancellation")
                .header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(count("booking where status = 'CANCELLED'")).isEqualTo(3);
        assertThat(count("booking where status = 'PENDING'")).isEqualTo(1);
    }

    @Test
    void cancellingASingleOccurrenceLeavesTheRestOfTheSeries() throws Exception {
        String id = createSeries(moderatorA, 4);
        String detail = mockMvc.perform(get("/api/booking-requests/" + id)
                .header(HttpHeaders.AUTHORIZATION, moderatorA)).andReturn().getResponse().getContentAsString();
        String bookingId = JsonPath.read(detail, "$.bookings[1].id");

        mockMvc.perform(post("/api/booking-requests/" + id + "/bookings/" + bookingId + "/cancellation")
                .header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(count("booking where status = 'CANCELLED'")).isEqualTo(1);
        assertThat(count("booking where status = 'PENDING'")).isEqualTo(3);
    }
}
