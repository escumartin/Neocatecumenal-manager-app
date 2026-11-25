package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import io.github.escumartin.neocatecumenal.identity.TestCommunityProvisionerConfig;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import io.github.escumartin.neocatecumenal.identity.UserDeleted;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Import(TestCommunityProvisionerConfig.class)
class AnonymizationIT {

    private static final LocalDate DATE = LocalDate.now().plusDays(10);
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId COMMUNITY = new CommunityId(UUID.randomUUID());

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @MockitoSpyBean
    private BookingRequestRepository requests;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbc;

    private final UserId deleted = new UserId(UUID.randomUUID());
    private final UserId other = new UserId(UUID.randomUUID());

    private Room room;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate event, booking_status_change, booking, booking_request, room cascade");
        room = rooms.save(Room.create(new Actor(other, ActorRole.ADMINISTRATOR, COMMUNITY), new RoomName("Main hall")));
    }

    private BookingRequest stored(UserId requester, UserId resolver, LocalDate date, TimeSlot slot) {
        Booking booking = new Booking(new BookingId(UUID.randomUUID()), date, slot, BookingStatus.APPROVED);
        List<StatusChange> history = List.of(
                new StatusChange(null, null, BookingRequestStatus.PENDING, requester, Instant.now()),
                new StatusChange(null, BookingRequestStatus.PENDING, BookingRequestStatus.APPROVED, resolver,
                        Instant.now()));
        return requests.save(BookingRequest.restore(new BookingRequestId(UUID.randomUUID()), room.id(), COMMUNITY,
                requester, new Reason("Catechesis"), null, date, date, slot, BookingRequestStatus.APPROVED,
                List.of(booking), history));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void deletingAUserClearsRequesterAndChangerAndKeepsTheBooking() {
        BookingRequest request = stored(deleted, deleted, DATE, SLOT);

        transaction.executeWithoutResult(status -> publisher.publishEvent(new UserDeleted(deleted.value())));

        assertThat(jdbc.queryForList("select requested_by from booking_request", UUID.class))
                .containsOnlyNulls();
        assertThat(count("booking_status_change where changed_by is not null")).isZero();
        assertThat(count("booking_status_change")).isEqualTo(2);
        assertThat(count("booking")).isEqualTo(1);
        BookingRequest found = requests.findById(request.id()).orElseThrow();
        assertThat(found.requestedBy()).isEmpty();
        assertThat(found.history()).extracting(StatusChange::changedBy).containsOnlyNulls();
        assertThat(found.status()).isEqualTo(BookingRequestStatus.APPROVED);
    }

    @Test
    void otherUsersKeepTheirIdentityInRequestsAndHistory() {
        BookingRequest mixed = stored(other, deleted, DATE, SLOT);
        BookingRequest untouched = stored(other, other, DATE.plusDays(1), SLOT);

        transaction.executeWithoutResult(status -> publisher.publishEvent(new UserDeleted(deleted.value())));

        assertThat(requests.findById(mixed.id()).orElseThrow().requestedBy()).contains(other);
        assertThat(requests.findById(mixed.id()).orElseThrow().history()).extracting(StatusChange::changedBy)
                .containsExactly(other, null);
        assertThat(requests.findById(untouched.id()).orElseThrow().history()).extracting(StatusChange::changedBy)
                .containsExactly(other, other);
    }

    @Test
    void aFailureAnonymizingRollsBackTheTransactionOfTheDeletion() {
        BookingRequest request = stored(deleted, deleted, DATE, SLOT);
        doThrow(new IllegalStateException("database unavailable")).when(requests).anonymize(any());

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("update booking_request set reason = 'Deleted with the user' where id = ?",
                    request.id().value());
            publisher.publishEvent(new UserDeleted(deleted.value()));
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("select reason from booking_request", String.class)).isEqualTo("Catechesis");
        assertThat(requests.findById(request.id()).orElseThrow().requestedBy()).contains(deleted);
    }
}
