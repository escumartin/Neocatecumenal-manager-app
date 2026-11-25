package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import io.github.escumartin.neocatecumenal.identity.TestCommunityProvisionerConfig;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestPage;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequest;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.Booking;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.RejectionReason;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.EventId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Import(TestCommunityProvisionerConfig.class)
class BookingRequestPersistenceIT {

    private static final LocalDateTime NOW = LocalDateTime.of(2030, 5, 10, 10, 0);
    private static final Instant AT = Instant.parse("2030-05-10T08:00:00Z");
    private static final TimeSlot SLOT = new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0));
    private static final CommunityId COMMUNITY_A = new CommunityId(UUID.randomUUID());
    private static final CommunityId COMMUNITY_B = new CommunityId(UUID.randomUUID());

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private BookingRequestRepository requests;

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR,
            COMMUNITY_A);
    private final Actor moderator = new Actor(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, COMMUNITY_A);

    private Room room;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate booking_status_change, booking, booking_request, room cascade");
        room = rooms.save(Room.create(administrator, new RoomName("Main hall")));
    }

    private BookingRequest newRequest(CommunityId community, LocalDate date, TimeSlot slot, String reason) {
        return BookingRequest.request(moderator, room.id(), community, date, slot, new Reason(reason), NOW, AT);
    }

    @Test
    void savesAndFindsARequestWithItsOccurrenceAndHistory() {
        BookingRequest saved = requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 11), SLOT, "Catechesis"));

        BookingRequest found = requests.findById(saved.id()).orElseThrow();

        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.roomId()).isEqualTo(room.id());
        assertThat(found.communityId()).isEqualTo(COMMUNITY_A);
        assertThat(found.requestedBy()).contains(moderator.userId());
        assertThat(found.reason()).isEqualTo(new Reason("Catechesis"));
        assertThat(found.firstDate()).isEqualTo(LocalDate.of(2030, 5, 11));
        assertThat(found.lastDate()).isEqualTo(LocalDate.of(2030, 5, 11));
        assertThat(found.slot()).isEqualTo(SLOT);
        assertThat(found.status()).isEqualTo(BookingRequestStatus.PENDING);
        assertThat(found.bookings()).isEqualTo(saved.bookings());
        assertThat(found.history()).isEqualTo(saved.history());
        assertThat(found.createdAt()).isEqualTo(AT);
    }

    @Test
    void savesAndFindsTheEventIdOfARequestForAnEvent() {
        EventId event = new EventId(UUID.randomUUID());
        BookingRequest saved = requests.save(BookingRequest.forEvent(moderator.userId(), COMMUNITY_A,
                room.id(), LocalDate.of(2030, 5, 11), SLOT, new Reason("Opening word"), event, NOW, AT));

        assertThat(requests.findById(saved.id()).orElseThrow().eventId()).contains(event);
        assertThat(jdbc.queryForObject("select event_id from booking_request where id = ?", UUID.class,
                saved.id().value())).isEqualTo(event.value());
    }

    @Test
    void aRequestWithoutEventHasNoEventId() {
        BookingRequest saved = requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 11), SLOT, "Catechesis"));

        assertThat(requests.findById(saved.id()).orElseThrow().eventId()).isEmpty();
    }

    @Test
    void findByIdOfAnUnknownRequestIsEmpty() {
        assertThat(requests.findById(new BookingRequestId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void storesTheRoomOfTheRequestInEachOccurrence() {
        BookingRequest saved = requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 11), SLOT, "Catechesis"));

        assertThat(jdbc.queryForObject("select room_id from booking where booking_request_id = ?", UUID.class,
                saved.id().value())).isEqualTo(room.id().value());
        assertThat(jdbc.queryForObject("select status from booking where booking_request_id = ?", String.class,
                saved.id().value())).isEqualTo(BookingStatus.PENDING.name());
    }

    @Test
    void storesTheCreationInTheStatusHistoryWithoutOccurrence() {
        BookingRequest saved = requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 11), SLOT, "Catechesis"));

        assertThat(jdbc.queryForObject("select count(*) from booking_status_change", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select to_status from booking_status_change", String.class))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select from_status from booking_status_change", String.class)).isNull();
        assertThat(jdbc.queryForObject("select booking_id from booking_status_change", UUID.class)).isNull();
        assertThat(jdbc.queryForObject("select changed_by from booking_status_change", UUID.class))
                .isEqualTo(moderator.userId().value());
        assertThat(saved.history()).hasSize(1);
    }

    @Test
    void storesAReasonOfFiveHundredCharacters() {
        BookingRequest saved = requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 11), SLOT,
                "x".repeat(500)));

        assertThat(requests.findById(saved.id()).orElseThrow().reason().value()).hasSize(500);
    }

    @Test
    void savingARectifiedRequestUpdatesItInPlaceKeepingTheHistory() {
        BookingRequest saved = requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 11), SLOT, "Catechesis"));
        saved.rectifyReason(moderator, new Reason("Youth catechesis"));
        requests.save(saved);

        BookingRequest found = requests.findById(saved.id()).orElseThrow();

        assertThat(found.reason()).isEqualTo(new Reason("Youth catechesis"));
        assertThat(jdbc.queryForObject("select count(*) from booking_request", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from booking", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from booking_status_change", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select version from booking_request", Long.class)).isEqualTo(1L);
    }

    @Test
    void aConcurrentModificationIsDetectedByTheVersion() {
        BookingRequest saved = requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 11), SLOT, "Catechesis"));
        JdbcTemplate otherConnection = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword()));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            BookingRequest loaded = requests.findById(saved.id()).orElseThrow();
            otherConnection.update("update booking_request set version = version + 1 where id = ?",
                    saved.id().value());
            loaded.rectifyReason(moderator, new Reason("Youth catechesis"));
            requests.save(loaded);
        })).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void searchFiltersByCommunityAndStatusAndOrdersByFirstDate() {
        BookingRequest later = requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 13), SLOT, "Later"));
        BookingRequest sooner = requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 11), SLOT, "Sooner"));
        BookingRequest other = requests.save(newRequest(COMMUNITY_B, LocalDate.of(2030, 5, 12), SLOT, "Other"));
        jdbc.update("update booking_request set status = 'APPROVED' where id = ?", other.id().value());

        assertThat(ids(requests.search(Optional.of(COMMUNITY_A), Set.of(), 0, 20)))
                .containsExactly(sooner.id(), later.id());
        assertThat(ids(requests.search(Optional.empty(), Set.of(), 0, 20)))
                .containsExactly(sooner.id(), other.id(), later.id());
        assertThat(ids(requests.search(Optional.empty(), Set.of(BookingRequestStatus.APPROVED), 0, 20)))
                .containsExactly(other.id());
        assertThat(ids(requests.search(Optional.of(COMMUNITY_B), Set.of(BookingRequestStatus.PENDING), 0, 20)))
                .isEmpty();
    }

    @Test
    void searchIsPaginated() {
        requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 11), SLOT, "One"));
        BookingRequest second = requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 12), SLOT, "Two"));
        requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 13), SLOT, "Three"));

        BookingRequestPage page = requests.search(Optional.empty(), Set.of(), 1, 1);

        assertThat(ids(page)).containsExactly(second.id());
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.totalItems()).isEqualTo(3);
    }

    @Test
    void persistsTheRejectionReasonStatusAndHistoryOfAResolution() {
        BookingRequest saved = requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 11), SLOT, "Catechesis"));
        BookingRequest loaded = requests.findById(saved.id()).orElseThrow();
        loaded.reject(administrator, new RejectionReason("Hall under maintenance"), AT);
        requests.save(loaded);

        BookingRequest found = requests.findById(saved.id()).orElseThrow();

        assertThat(found.status()).isEqualTo(BookingRequestStatus.REJECTED);
        assertThat(found.rejectionReason()).contains(new RejectionReason("Hall under maintenance"));
        assertThat(found.bookings()).extracting(Booking::status).containsOnly(BookingStatus.REJECTED);
        assertThat(found.history()).hasSize(2);
    }

    @Test
    void anApprovalLeavesNoRejectionReason() {
        BookingRequest saved = requests.save(newRequest(COMMUNITY_A, LocalDate.of(2030, 5, 11), SLOT, "Catechesis"));
        BookingRequest loaded = requests.findById(saved.id()).orElseThrow();
        loaded.approve(administrator, NOW, AT);
        requests.save(loaded);

        BookingRequest found = requests.findById(saved.id()).orElseThrow();

        assertThat(found.status()).isEqualTo(BookingRequestStatus.APPROVED);
        assertThat(found.rejectionReason()).isEmpty();
    }

    @Test
    void theTableKeepsOnlyTheColumnsOfTheDataModel() {
        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = 'booking_request'",
                String.class);

        assertThat(columns).containsExactlyInAnyOrder("id", "room_id", "community_id", "requested_by", "event_id",
                "reason", "first_date", "last_date", "start_time", "end_time", "recurrence_until", "status",
                "rejection_reason", "version", "created_at");
    }

    private static List<BookingRequestId> ids(BookingRequestPage page) {
        return page.items().stream().map(BookingRequest::id).toList();
    }
}
