package io.github.escumartin.neocatecumenal.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.agenda.AgendaApi;
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
class EventHistoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private AgendaApi agenda;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate event, booking_status_change, booking, booking_request, room cascade");
    }

    private void eventOf(UUID community) {
        jdbc.update("insert into event (id, community_id, type, title, event_date, start_time, end_time, room_id, "
                + "booking_request_id, created_at) values (?, ?, 'WORD', 'Opening word', '2030-05-10', '18:00', "
                + "'19:00', ?, ?, now())", UUID.randomUUID(), community, UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void isTrueWhenAtLeastOneEventReferencesTheCommunity() {
        UUID community = UUID.randomUUID();
        eventOf(community);

        assertThat(agenda.hasEventsFor(community)).isTrue();
    }

    @Test
    void isFalseWhenOnlyOtherCommunitiesHaveEvents() {
        eventOf(UUID.randomUUID());

        assertThat(agenda.hasEventsFor(UUID.randomUUID())).isFalse();
    }

    @Test
    void isFalseWhenThereAreNoEvents() {
        assertThat(agenda.hasEventsFor(UUID.randomUUID())).isFalse();
    }
}
