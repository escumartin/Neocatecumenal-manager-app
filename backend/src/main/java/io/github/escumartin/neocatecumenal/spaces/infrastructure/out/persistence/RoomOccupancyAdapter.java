package io.github.escumartin.neocatecumenal.spaces.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.OccupiedBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomOccupancyQuery;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingStatus;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RoomOccupancyAdapter implements RoomOccupancyQuery {

    private final BookingRequestSpringDataRepository repository;

    RoomOccupancyAdapter(BookingRequestSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OccupiedBooking> occupiedSlots(RoomId roomId, LocalDate date) {
        return repository.findOccupancy(roomId.value(), date, BookingStatus.activeStatuses()).stream()
                .map(row -> new OccupiedBooking(new TimeSlot(row.startTime(), row.endTime()), row.status(),
                        new CommunityId(row.communityId())))
                .toList();
    }
}
