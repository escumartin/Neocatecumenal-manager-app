package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.RequestBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import io.github.escumartin.neocatecumenal.spaces.domain.WeeklyRecurrence;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
class SeriesPerformanceIT {

    private static final int OCCURRENCES = 53;
    private static final int WARM_UP_RUNS = 5;
    private static final int MEASURED_RUNS = 40;
    private static final Duration BUDGET = Duration.ofMillis(500);
    private static final LocalDate FIRST = LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(7);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId COMMUNITY = new CommunityId(UUID.randomUUID());

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private RequestBooking requestBooking;

    @Autowired
    private JdbcTemplate jdbc;

    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, COMMUNITY);
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY);

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate event, booking_status_change, booking, booking_request, room cascade");
        jdbc.update("insert into identity_community (id, number, active) values (?, 1, true) "
                + "on conflict (id) do nothing", COMMUNITY.value());
    }

    private Duration timeOneSeries(int index) {
        Room room = rooms.save(Room.create(administrator, new RoomName("Hall " + index)));
        long start = System.nanoTime();
        BookingRequest created = requestBooking.request(moderator, room.id(), null, FIRST, SLOT,
                new Reason("Catechesis"), new WeeklyRecurrence(FIRST.plusYears(1)));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(created.bookings()).hasSize(OCCURRENCES);
        return elapsed;
    }

    @Test
    void creatingAFiftyThreeOccurrenceSeriesStaysUnderTheBudgetAtThe95thPercentile() {
        for (int run = 0; run < WARM_UP_RUNS; run++) {
            timeOneSeries(run);
        }
        List<Duration> measured = new ArrayList<>();
        for (int run = 0; run < MEASURED_RUNS; run++) {
            measured.add(timeOneSeries(WARM_UP_RUNS + run));
        }
        Collections.sort(measured);

        Duration p95 = measured.get((int) Math.ceil(MEASURED_RUNS * 0.95) - 1);

        assertThat(p95).isLessThan(BUDGET);
        assertThat(jdbc.queryForObject("select count(*) from booking", Integer.class))
                .isEqualTo((WARM_UP_RUNS + MEASURED_RUNS) * OCCURRENCES);
    }
}
