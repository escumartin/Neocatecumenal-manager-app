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
class BookingRequestsControllerTest {

    private static final AtomicInteger ACCESS_IDS = new AtomicInteger(400000);

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
    void aModeratorRequestsARoomForTheirCommunity() throws Exception {
        String body = mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA, defaultRequest(null)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/booking-requests/")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.communityId").value(communityA.id().value().toString()))
                .andExpect(jsonPath("$.roomId").value(roomId))
                .andExpect(jsonPath("$.date").value(tomorrow()))
                .andExpect(jsonPath("$.startTime").value("18:00"))
                .andExpect(jsonPath("$.endTime").value("20:00"))
                .andExpect(jsonPath("$.reason").value("Catechesis"))
                .andExpect(jsonPath("$.occurrenceCount").value(1))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.id");
        assertThat(jdbc.queryForObject("select status from booking_request where id = ?::uuid", String.class, id))
                .isEqualTo("PENDING");
        assertThat(count("booking")).isEqualTo(1);
    }

    @Test
    void aModeratorMayNameTheirOwnCommunity() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                defaultRequest(communityA.id().value().toString()))).andExpect(status().isCreated());
    }

    @Test
    void anAdministratorRequestsForTheChosenCommunity() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), administrator,
                defaultRequest(communityB.id().value().toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.communityId").value(communityB.id().value().toString()));
    }

    @Test
    void anAdministratorWithoutACommunityIsRequiredToChooseOne() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), administrator, defaultRequest(null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("communityId"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_REQUIRED"));
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void anAdministratorChoosingAnUnknownCommunityIsRejected() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), administrator,
                defaultRequest(UUID.randomUUID().toString())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("communityId"))
                .andExpect(jsonPath("$.errors[0].code").value("COMMUNITY_NOT_FOUND"));
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void aModeratorCannotRequestForAnotherCommunity() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                defaultRequest(communityB.id().value().toString())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void aRegisteredUserCannotRequest() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), registered, defaultRequest(null)))
                .andExpect(status().isForbidden());
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void requestingAnUnknownRoomIsNotFound() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                requestJson(UUID.randomUUID().toString(), null, tomorrow(), "18:00", "20:00", "Catechesis")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void anOverlappingRequestIsAConflictWithTheOccupiedSlotAndStoresNothing() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA, defaultRequest(null)))
                .andExpect(status().isCreated());

        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorB,
                requestJson(roomId, null, tomorrow(), "19:00", "21:00", "Rehearsal")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_OCCUPIED"))
                .andExpect(jsonPath("$.conflicts", hasSize(1)))
                .andExpect(jsonPath("$.conflicts[0].date").value(tomorrow()))
                .andExpect(jsonPath("$.conflicts[0].startTime").value("18:00"))
                .andExpect(jsonPath("$.conflicts[0].endTime").value("20:00"));
        assertThat(count("booking_request")).isEqualTo(1);
        assertThat(count("booking")).isEqualTo(1);
    }

    @Test
    void aContiguousRequestIsAccepted() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA, defaultRequest(null)))
                .andExpect(status().isCreated());

        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorB,
                requestJson(roomId, null, tomorrow(), "20:00", "21:00", "Rehearsal")))
                .andExpect(status().isCreated());
    }

    @Test
    void requestingAnInactiveRoomIsAConflict() throws Exception {
        mockMvc.perform(post("/api/rooms/" + roomId + "/deactivation").header(HttpHeaders.AUTHORIZATION,
                administrator)).andExpect(status().isNoContent());

        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA, defaultRequest(null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_INACTIVE"));
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void anEndNotAfterTheStartIsRejectedAndNothingIsStored() throws Exception {
        for (String end : new String[] {"18:00", "17:00"}) {
            mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                    requestJson(roomId, null, tomorrow(), "18:00", end, "Catechesis")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].field").value("endTime"))
                    .andExpect(jsonPath("$.errors[0].code").value("END_NOT_AFTER_START"));
        }
        assertThat(count("booking_request")).isZero();
        assertThat(count("booking")).isZero();
    }

    @Test
    void aPastDateIsRejectedAndNothingIsStored() throws Exception {
        String yesterday = LocalDate.now(ZoneId.of("Europe/Madrid")).minusDays(1).toString();

        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                requestJson(roomId, null, yesterday, "18:00", "20:00", "Catechesis")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("startTime"))
                .andExpect(jsonPath("$.errors[0].code").value("START_NOT_IN_FUTURE"));
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void aTodaySlotThatAlreadyStartedIsRejected() throws Exception {
        String today = LocalDate.now(ZoneId.of("Europe/Madrid")).toString();

        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                requestJson(roomId, null, today, "00:00", "00:01", "Catechesis")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("START_NOT_IN_FUTURE"));
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void aReasonOfFiveHundredCharactersIsAccepted() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                requestJson(roomId, null, tomorrow(), "18:00", "20:00", "x".repeat(500))))
                .andExpect(status().isCreated());
    }

    @Test
    void aReasonOfFiveHundredAndOneCharactersIsTooLong() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                requestJson(roomId, null, tomorrow(), "18:00", "20:00", "x".repeat(501))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("reason"))
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void aReasonWithANulCharacterIsInvalidFormat() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                requestJson(roomId, null, tomorrow(), "18:00", "20:00", "Cate\\u0000chesis")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("reason"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
        assertThat(count("booking_request")).isZero();
    }

    @Test
    void aBlankReasonIsRequired() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                requestJson(roomId, null, tomorrow(), "18:00", "20:00", "   ")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("reason"))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED"));
    }

    @Test
    void aMalformedTimeIsInvalidFormat() throws Exception {
        mockMvc.perform(asJson(post("/api/booking-requests"), moderatorA,
                requestJson(roomId, null, tomorrow(), "6pm", "20:00", "Catechesis")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("startTime"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void aModeratorListsOnlyTheRequestsOfTheirCommunity() throws Exception {
        String ownId = createRequest(moderatorA, null);
        createRequest(moderatorB, null);

        mockMvc.perform(get("/api/booking-requests").header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(ownId))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void anAdministratorListsEveryCommunityAndCanFilterByCommunityAndStatus() throws Exception {
        createRequest(moderatorA, null);
        createRequest(moderatorB, null);

        mockMvc.perform(get("/api/booking-requests").header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalItems").value(2));
        mockMvc.perform(get("/api/booking-requests").param("communityId", communityB.id().value().toString())
                .header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].communityId").value(communityB.id().value().toString()));
        mockMvc.perform(get("/api/booking-requests").param("status", "APPROVED")
                .header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get("/api/booking-requests").param("status", "PENDING", "REJECTED")
                .header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void listingIsPaginated() throws Exception {
        createRequest(moderatorA, null);
        createRequest(moderatorA, null);

        mockMvc.perform(get("/api/booking-requests").param("page", "1").param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void aModeratorCannotListAnotherCommunity() throws Exception {
        mockMvc.perform(get("/api/booking-requests").param("communityId", communityB.id().value().toString())
                .header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isForbidden());
    }

    @Test
    void aRegisteredUserCannotList() throws Exception {
        mockMvc.perform(get("/api/booking-requests").header(HttpHeaders.AUTHORIZATION, registered))
                .andExpect(status().isForbidden());
    }

    @Test
    void theDetailShowsOccurrencesAndHistory() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(get("/api/booking-requests/" + id).header(HttpHeaders.AUTHORIZATION, moderatorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.bookings", hasSize(1)))
                .andExpect(jsonPath("$.bookings[0].status").value("PENDING"))
                .andExpect(jsonPath("$.bookings[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.history", hasSize(1)))
                .andExpect(jsonPath("$.history[0].toStatus").value("PENDING"))
                .andExpect(jsonPath("$.history[0].changedBy").isNotEmpty())
                .andExpect(jsonPath("$.history[0].changedAt").isNotEmpty());
    }

    @Test
    void theDetailOfAnotherCommunityIsNotFoundForAModeratorButVisibleToAnAdministrator() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(get("/api/booking-requests/" + id).header(HttpHeaders.AUTHORIZATION, moderatorB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/booking-requests/" + id).header(HttpHeaders.AUTHORIZATION, administrator))
                .andExpect(status().isOk());
    }

    @Test
    void theDetailOfAnUnknownRequestIsNotFound() throws Exception {
        mockMvc.perform(get("/api/booking-requests/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION,
                administrator)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void aModeratorRectifiesTheReasonOfAPendingRequest() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(asJson(put("/api/booking-requests/" + id + "/reason"), moderatorA,
                "{\"reason\":\"Youth catechesis\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Youth catechesis"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(jdbc.queryForObject("select reason from booking_request where id = ?::uuid", String.class, id))
                .isEqualTo("Youth catechesis");
        assertThat(count("booking_status_change")).isEqualTo(1);
    }

    @Test
    void rectifyingAnApprovedRequestIsAConflict() throws Exception {
        String id = createRequest(moderatorA, null);
        jdbc.update("update booking_request set status = 'APPROVED' where id = ?::uuid", id);

        mockMvc.perform(asJson(put("/api/booking-requests/" + id + "/reason"), moderatorA,
                "{\"reason\":\"Youth catechesis\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_REQUEST_ALREADY_RESOLVED"));
    }

    @Test
    void aModeratorCannotRectifyTheReasonOfAnotherCommunity() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(asJson(put("/api/booking-requests/" + id + "/reason"), moderatorB,
                "{\"reason\":\"Youth catechesis\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select reason from booking_request where id = ?::uuid", String.class, id))
                .isEqualTo("Catechesis");
    }

    @Test
    void rectifyingWithATooLongReasonIsRejected() throws Exception {
        String id = createRequest(moderatorA, null);

        mockMvc.perform(asJson(put("/api/booking-requests/" + id + "/reason"), moderatorA,
                "{\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("TOO_LONG"));
    }

    @Test
    void rectifyingAnUnknownRequestIsNotFound() throws Exception {
        mockMvc.perform(asJson(put("/api/booking-requests/" + UUID.randomUUID() + "/reason"), moderatorA,
                "{\"reason\":\"Youth catechesis\"}")).andExpect(status().isNotFound());
    }

    @Test
    void requestsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/booking-requests")).andExpect(status().isUnauthorized());
    }
}
