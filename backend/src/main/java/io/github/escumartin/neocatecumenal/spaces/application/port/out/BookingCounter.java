package io.github.escumartin.neocatecumenal.spaces.application.port.out;

import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import java.time.LocalDateTime;

public interface BookingCounter {

    int activeFutureBookings(RoomId roomId, LocalDateTime now);
}
