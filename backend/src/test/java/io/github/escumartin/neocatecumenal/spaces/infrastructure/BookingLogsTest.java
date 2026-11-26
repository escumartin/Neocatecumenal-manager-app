package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.CancelBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.RejectBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.RequestBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.RejectionReason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomOccupied;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class BookingLogsTest {

    private static final String REASON_SENTINEL = "Zzyzx catechesis sentinel";
    private static final String REJECTION_SENTINEL = "Plugh rejection sentinel";
    private static final LocalDate DATE = LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(10);
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
    private RejectBookingRequest rejectBookingRequest;

    @Autowired
    private CancelBookingRequest cancelBookingRequest;

    @Autowired
    private JdbcTemplate jdbc;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, COMMUNITY);
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY);

    private Logger rootLogger;
    private Logger applicationLogger;
    private Level previousApplicationLevel;
    private Room room;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate event, booking_status_change, booking, booking_request, room cascade");
        jdbc.update("insert into identity_community (id, number, active) values (?, 1, true) "
                + "on conflict (id) do nothing", COMMUNITY.value());
        room = rooms.save(Room.create(administrator, new RoomName("Main hall")));
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        applicationLogger = (Logger) LoggerFactory.getLogger("io.github.escumartin.neocatecumenal");
        previousApplicationLevel = applicationLogger.getLevel();
        applicationLogger.setLevel(Level.TRACE);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
        appender.stop();
        applicationLogger.setLevel(previousApplicationLevel);
    }

    private String everythingLogged() {
        List<String> lines = new ArrayList<>();
        for (ILoggingEvent event : new ArrayList<>(appender.list)) {
            lines.add(event.getFormattedMessage());
            lines.add(String.valueOf(event.getKeyValuePairs()));
            lines.add(String.valueOf(event.getArgumentArray() == null ? "" : List.of(event.getArgumentArray())));
            lines.add(String.valueOf(event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage()));
        }
        return String.join("\n", lines);
    }

    private BookingRequest request(LocalDate date) {
        return requestBooking.request(moderator, room.id(), null, date, SLOT, new Reason(REASON_SENTINEL));
    }

    @Test
    void creatingARequestLogsItsIdAndNeverItsReason() {
        BookingRequest created = request(DATE);

        assertThat(everythingLogged())
                .contains("Solicitud de salón registrada")
                .contains(created.id().value().toString())
                .doesNotContain(REASON_SENTINEL);
    }

    @Test
    void rejectingARequestLogsNeitherTheReasonNorTheRejectionReason() {
        BookingRequest created = request(DATE);

        rejectBookingRequest.reject(administrator, created.id(), new RejectionReason(REJECTION_SENTINEL));

        assertThat(everythingLogged())
                .contains("Solicitud de salón rechazada")
                .doesNotContain(REASON_SENTINEL)
                .doesNotContain(REJECTION_SENTINEL);
    }

    @Test
    void cancellingARequestLogsOnlyItsId() {
        BookingRequest created = request(DATE);

        cancelBookingRequest.cancel(moderator, created.id());

        assertThat(everythingLogged())
                .contains("Solicitud de salón cancelada")
                .contains(created.id().value().toString())
                .doesNotContain(REASON_SENTINEL);
    }

    @Test
    void aRejectedConflictLogsOnlyTheNumberOfConflicts() {
        request(DATE);

        assertThatThrownBy(() -> request(DATE)).isInstanceOf(RoomOccupied.class);

        assertThat(everythingLogged()).doesNotContain(REASON_SENTINEL);
    }
}
