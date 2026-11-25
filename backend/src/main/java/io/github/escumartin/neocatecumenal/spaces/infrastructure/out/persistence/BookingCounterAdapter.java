package io.github.escumartin.neocatecumenal.spaces.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingCounter;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class BookingCounterAdapter implements BookingCounter {

    private static final String COUNT_ACTIVE_FUTURE = "select count(*) from booking "
            + "where room_id = :roomId and status in ('PENDING', 'APPROVED') "
            + "and (booking_date + start_time) > :now";

    private final EntityManager entityManager;

    BookingCounterAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public int activeFutureBookings(RoomId roomId, LocalDateTime now) {
        Number count = (Number) entityManager.createNativeQuery(COUNT_ACTIVE_FUTURE)
                .setParameter("roomId", roomId.value())
                .setParameter("now", now)
                .getSingleResult();
        return count.intValue();
    }
}
