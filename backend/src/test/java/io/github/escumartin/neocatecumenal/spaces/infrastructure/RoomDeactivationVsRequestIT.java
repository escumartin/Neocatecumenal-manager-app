package io.github.escumartin.neocatecumenal.spaces.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.spaces.application.port.in.DeactivateRoom;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.RequestBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.ActorRole;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.Reason;
import io.github.escumartin.neocatecumenal.spaces.domain.Room;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomHasActiveBookings;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomInactive;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomName;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.spaces.domain.UserId;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class RoomDeactivationVsRequestIT {

    private static final int TIMEOUT_SECONDS = 20;
    private static final CommunityId COMMUNITY = new CommunityId(UUID.randomUUID());

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private RoomRepository rooms;

    @Autowired
    private RequestBooking requestBooking;

    @Autowired
    private DeactivateRoom deactivateRoom;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR,
            COMMUNITY);
    private Room room;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        jdbc.execute("truncate booking_status_change, booking, booking_request, room cascade");
        room = rooms.save(Room.create(administrator, new RoomName("Main hall")));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    private void request() {
        requestBooking.request(administrator, room.id(), COMMUNITY, LocalDate.now(ZoneId.of("Europe/Madrid"))
                .plusDays(7), new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0)), new Reason("Catechesis"));
    }

    private void awaitBlockedBackend(Future<?> blocked) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (blocked.isDone()) {
                blocked.get();
                throw new AssertionError("The operation finished without waiting for the lock");
            }
            Integer waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname = "
                    + "current_database() and cardinality(pg_blocking_pids(pid)) > 0", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No backend is waiting for a lock");
    }

    private int activeBookingsInRoom() {
        return jdbc.queryForObject("select count(*) from booking where room_id = ? and status in "
                + "('PENDING', 'APPROVED')", Integer.class, room.id().value());
    }

    private boolean roomIsActive() {
        return jdbc.queryForObject("select active from room where id = ?", Boolean.class, room.id().value());
    }

    @Test
    void aRequestWaitingForTheDeactivationFindsTheRoomInactive() throws Exception {
        CountDownLatch roomLocked = new CountDownLatch(1);
        CountDownLatch requestBlocked = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        CompletableFuture<Void> deactivation = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(
                status -> {
                    rooms.findByIdForUpdate(room.id());
                    roomLocked.countDown();
                    await(requestBlocked);
                    deactivateRoom.deactivate(administrator, room.id());
                }), executor);
        assertThat(roomLocked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> request = CompletableFuture.runAsync(this::request, executor);
        try {
            awaitBlockedBackend(request);
        } finally {
            requestBlocked.countDown();
        }

        deactivation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThatThrownBy(() -> request.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(RoomInactive.class);
        assertThat(roomIsActive()).isFalse();
        assertThat(activeBookingsInRoom()).isZero();
    }

    @Test
    void aDeactivationWaitingForTheRequestSeesItsBooking() throws Exception {
        CountDownLatch requestDone = new CountDownLatch(1);
        CountDownLatch deactivationBlocked = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        CompletableFuture<Void> request = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(
                status -> {
                    request();
                    requestDone.countDown();
                    await(deactivationBlocked);
                }), executor);
        assertThat(requestDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Void> deactivation = CompletableFuture.runAsync(
                () -> deactivateRoom.deactivate(administrator, room.id()), executor);
        try {
            awaitBlockedBackend(deactivation);
        } finally {
            deactivationBlocked.countDown();
        }

        request.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThatThrownBy(() -> deactivation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(RoomHasActiveBookings.class);
        assertThat(roomIsActive()).isTrue();
        assertThat(activeBookingsInRoom()).isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
