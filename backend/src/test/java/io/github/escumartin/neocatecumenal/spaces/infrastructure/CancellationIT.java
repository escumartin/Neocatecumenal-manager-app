package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import io.github.escumartin.neocatecumenal.identity.TestCommunityProvisionerConfig;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.CancelBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.CancelBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.RequestBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.Booking;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.StatusChange;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
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
@Import(TestCommunityProvisionerConfig.class)
class CancellationIT {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Europe/Madrid"));
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId COMMUNITY = new CommunityId(UUID.randomUUID());

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private BookingRequestRepository requests;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private CancelBookingRequest cancelRequest;

    @Autowired
    private CancelBooking cancelBooking;

    @Autowired
    private RequestBooking requestBooking;

    @Autowired
    private JdbcTemplate jdbc;

    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, COMMUNITY);
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY);

    private Room room;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate booking_status_change, booking, booking_request, room cascade");
        room = rooms.save(Room.create(administrator, new RoomName("Main hall")));
    }

    private BookingRequest approvedSeries(int... offsetsInDays) {
        List<Booking> occurrences = Arrays.stream(offsetsInDays)
                .mapToObj(offset -> new Booking(new BookingId(UUID.randomUUID()), TODAY.plusDays(offset), SLOT,
                        BookingStatus.APPROVED))
                .toList();
        StatusChange created = new StatusChange(null, null, BookingRequestStatus.PENDING, moderator.userId(),
                Instant.now());
        return requests.save(BookingRequest.restore(new BookingRequestId(UUID.randomUUID()), room.id(), COMMUNITY,
                moderator.userId(), new Reason("Catechesis"), null, occurrences.get(0).date(),
                occurrences.get(occurrences.size() - 1).date(), SLOT, BookingRequestStatus.APPROVED, occurrences,
                List.of(created)));
    }

    private List<BookingStatus> statusesOf(BookingRequest request) {
        return requests.findById(request.id()).orElseThrow().bookings().stream().map(Booking::status).toList();
    }

    @Test
    void cancellingASeriesCancelsTheFutureOccurrencesAndKeepsThePastOnes() {
        BookingRequest series = approvedSeries(-14, -7, 7, 14);

        cancelRequest.cancel(moderator, series.id());

        assertThat(statusesOf(series)).containsExactly(BookingStatus.APPROVED, BookingStatus.APPROVED,
                BookingStatus.CANCELLED, BookingStatus.CANCELLED);
        assertThat(requests.findById(series.id()).orElseThrow().status()).isEqualTo(BookingRequestStatus.CANCELLED);
        assertThat(jdbc.queryForObject("select count(*) from booking_status_change where to_status = 'CANCELLED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void cancellingASingleOccurrenceDoesNotAffectTheOthers() {
        BookingRequest series = approvedSeries(7, 14, 21);
        BookingId middle = series.bookings().get(1).id();

        cancelBooking.cancel(moderator, series.id(), middle);

        assertThat(statusesOf(series)).containsExactly(BookingStatus.APPROVED, BookingStatus.CANCELLED,
                BookingStatus.APPROVED);
        BookingRequest stored = requests.findById(series.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(BookingRequestStatus.APPROVED);
        assertThat(stored.history()).extracting(StatusChange::bookingId).containsExactly(null, middle);
        assertThat(jdbc.queryForObject("select booking_id from booking_status_change where to_status = 'CANCELLED'",
                UUID.class)).isEqualTo(middle.value());
    }

    @Test
    void cancellingTheLastActiveOccurrencePersistsTheRequestAsCancelled() {
        BookingRequest series = approvedSeries(7);

        cancelBooking.cancel(administrator, series.id(), series.bookings().get(0).id());

        assertThat(requests.findById(series.id()).orElseThrow().status()).isEqualTo(BookingRequestStatus.CANCELLED);
        assertThat(jdbc.queryForObject("select count(*) from booking_status_change", Integer.class)).isEqualTo(3);
    }

    @Test
    void aCancelledSlotAdmitsANewRequest() {
        BookingRequest series = approvedSeries(7);
        cancelRequest.cancel(moderator, series.id());

        BookingRequest created = requestBooking.request(moderator, room.id(), null, TODAY.plusDays(7), SLOT,
                new Reason("Rehearsal"));

        assertThat(created.status()).isEqualTo(BookingRequestStatus.PENDING);
        assertThat(jdbc.queryForObject("select count(*) from booking where status in ('PENDING', 'APPROVED')",
                Integer.class)).isEqualTo(1);
    }
}
