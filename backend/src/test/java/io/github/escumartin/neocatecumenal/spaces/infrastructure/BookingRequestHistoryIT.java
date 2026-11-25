package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.spaces.SpacesApi;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class BookingRequestHistoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private SpacesApi spaces;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID room;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate booking_status_change, booking, booking_request, room cascade");
        room = UUID.randomUUID();
        jdbc.update("insert into room (id, name, active, version, created_at) values (?, 'Main hall', true, 0, now())",
                room);
    }

    private void bookingRequestOf(UUID community, String status) {
        jdbc.update("insert into booking_request (id, room_id, community_id, reason, first_date, last_date, "
                + "start_time, end_time, status, version, created_at) values (?, ?, ?, 'Meeting', "
                + "'2030-05-10', '2030-05-10', '18:00', '19:00', ?, 0, now())",
                UUID.randomUUID(), room, community, status);
    }

    @Test
    void isTrueWhenAtLeastOneRequestReferencesTheCommunity() {
        UUID community = UUID.randomUUID();
        bookingRequestOf(community, "PENDING");

        assertThat(spaces.hasBookingRequestsFor(community)).isTrue();
    }

    @Test
    void isTrueRegardlessOfTheRequestStatus() {
        UUID community = UUID.randomUUID();
        bookingRequestOf(community, "CANCELLED");

        assertThat(spaces.hasBookingRequestsFor(community)).isTrue();
    }

    @Test
    void isFalseWhenOnlyOtherCommunitiesHaveRequests() {
        bookingRequestOf(UUID.randomUUID(), "APPROVED");

        assertThat(spaces.hasBookingRequestsFor(UUID.randomUUID())).isFalse();
    }

    @Test
    void isFalseWhenThereAreNoRequests() {
        assertThat(spaces.hasBookingRequestsFor(UUID.randomUUID())).isFalse();
    }
}
