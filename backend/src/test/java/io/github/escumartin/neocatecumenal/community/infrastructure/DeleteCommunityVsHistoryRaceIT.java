package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

class DeleteCommunityVsHistoryRaceIT extends AbstractRaceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final int ROUNDS = 15;

    private UUID room;

    @BeforeEach
    void setUpRoom() {
        room = fixture.room();
    }

    private Callable<RaceHarness.Response> deleteCommunity(UUID community) {
        return () -> harness.send(delete("/api/communities/" + community)
                .header(HttpHeaders.AUTHORIZATION, administratorSession()));
    }

    private Callable<RaceHarness.Response> requestBooking(UUID community, UUID room, int hour) {
        String body = "{\"roomId\":\"" + room + "\",\"communityId\":\"" + community + "\",\"date\":\"" + tomorrow()
                + "\",\"startTime\":\"" + time(hour) + "\",\"endTime\":\"" + time(hour + 1)
                + "\",\"reason\":\"Test reason\"}";
        return () -> harness.send(json(post("/api/booking-requests"), body));
    }

    private Callable<RaceHarness.Response> createEvent(UUID community, UUID room, int hour) {
        String body = "{\"type\":\"WORD\",\"title\":\"Opening word\",\"roomId\":\"" + room + "\",\"communityId\":\""
                + community + "\",\"date\":\"" + tomorrow() + "\",\"startTime\":\"" + time(hour)
                + "\",\"endTime\":\"" + time(hour + 1) + "\"}";
        return () -> harness.send(json(post("/api/events"), body));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.header(HttpHeaders.AUTHORIZATION, administratorSession())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String tomorrow() {
        return LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(1).toString();
    }

    private static String time(int hour) {
        return String.format("%02d:00", hour);
    }

    private int orphanBookingRequests() {
        return jdbc.queryForObject("select count(*) from booking_request where community_id not in "
                + "(select id from identity_community)", Integer.class);
    }

    private int orphanEvents() {
        return jdbc.queryForObject("select count(*) from event where community_id not in "
                + "(select id from identity_community)", Integer.class);
    }

    @Test
    void aBookingRequestCreatedWhileTheDeletionIsInFlightBlocksTheDeletion() throws Exception {
        List<RaceHarness.Response> responses = harness.blockedWhileAnotherRuns(communityB,
                deleteCommunity(communityB), requestBooking(communityB, room, 10));

        assertThat(responses.get(1).status()).isEqualTo(201);
        assertThat(responses.get(0).status()).isEqualTo(409);
        assertThat(responses.get(0).code()).isEqualTo("COMMUNITY_HAS_HISTORY");
        assertThat(orphanBookingRequests()).isZero();
    }

    @Test
    void anEventCreatedWhileTheDeletionIsInFlightBlocksTheDeletion() throws Exception {
        List<RaceHarness.Response> responses = harness.blockedWhileAnotherRuns(communityB,
                deleteCommunity(communityB), createEvent(communityB, room, 10));

        assertThat(responses.get(1).status()).isEqualTo(201);
        assertThat(responses.get(0).status()).isEqualTo(409);
        assertThat(responses.get(0).code()).isEqualTo("COMMUNITY_HAS_HISTORY");
        assertThat(orphanEvents()).isZero();
        assertThat(orphanBookingRequests()).isZero();
    }

    @Test
    void aBookingRequestQueuedBehindTheDeletionIsRejectedAsCommunityNotFound() throws Exception {
        List<RaceHarness.Response> responses = harness.queuedBehindIdentityCommunity(communityB,
                List.of(deleteCommunity(communityB), requestBooking(communityB, room, 10)));

        assertThat(responses.get(0).status()).isEqualTo(204);
        assertThat(responses.get(1).status()).isEqualTo(422);
        assertThat(responses.get(1).code()).isEqualTo("COMMUNITY_NOT_FOUND");
        assertThat(orphanBookingRequests()).isZero();
    }

    @Test
    void anEventQueuedBehindTheDeletionIsRejectedAsCommunityNotFound() throws Exception {
        List<RaceHarness.Response> responses = harness.queuedBehindIdentityCommunity(communityB,
                List.of(deleteCommunity(communityB), createEvent(communityB, room, 10)));

        assertThat(responses.get(0).status()).isEqualTo(204);
        assertThat(responses.get(1).status()).isEqualTo(422);
        assertThat(responses.get(1).code()).isEqualTo("COMMUNITY_NOT_FOUND");
        assertThat(orphanEvents()).isZero();
        assertThat(orphanBookingRequests()).isZero();
    }

    @Test
    void deletingAndRequestingABookingNeverLeaveAnOrphanBookingRequest() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID community = fixture.community(100 + round);

            List<RaceHarness.Response> responses = harness.simultaneously(List.of(
                    deleteCommunity(community), requestBooking(community, fixture.room(), 10)));

            assertThat(responses.get(0).status()).isIn(204, 409);
            assertThat(responses.get(1).status()).isIn(201, 422);
            assertThat(responses.get(0).status() == 204).isEqualTo(responses.get(1).status() == 422);
            assertThat(orphanBookingRequests()).isZero();
        }
        harness.assertNoDeadlocksSinceStart();
    }

    @Test
    void deletingAndCreatingAnEventNeverLeaveAnOrphanEvent() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID community = fixture.community(100 + round);

            List<RaceHarness.Response> responses = harness.simultaneously(List.of(
                    deleteCommunity(community), createEvent(community, fixture.room(), 10)));

            assertThat(responses.get(0).status()).isIn(204, 409);
            assertThat(responses.get(1).status()).isIn(201, 422);
            assertThat(responses.get(0).status() == 204).isEqualTo(responses.get(1).status() == 422);
            assertThat(orphanEvents()).isZero();
            assertThat(orphanBookingRequests()).isZero();
        }
        harness.assertNoDeadlocksSinceStart();
    }
}
