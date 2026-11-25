package io.github.escumartin.neocatecumenal.spaces.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.spaces.BookingRequestState;
import io.github.escumartin.neocatecumenal.spaces.EventBookingCommand;
import io.github.escumartin.neocatecumenal.spaces.EventBookingConflict;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult.Booked;
import io.github.escumartin.neocatecumenal.spaces.EventBookingResult.Occupied;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.BookRoomForEvent;
import io.github.escumartin.neocatecumenal.spaces.application.service.SpacesApiAdapter;
import io.github.escumartin.neocatecumenal.spaces.domain.Conflict;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomOccupied;
import io.github.escumartin.neocatecumenal.spaces.domain.TimeSlot;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpacesApiAdapterTest {

    private static final LocalDate DATE = LocalDate.of(2030, 5, 11);

    private static EventBookingCommand command() {
        return new EventBookingCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                DATE, LocalTime.of(18, 0), LocalTime.of(20, 0), "Opening word");
    }

    @Test
    void aBookedResultPassesThrough() {
        UUID id = UUID.randomUUID();
        BookRoomForEvent delegate = new StubBookRoomForEvent(new Booked(id), null);

        assertThat(new SpacesApiAdapter(delegate).requestForEvent(command())).isEqualTo(new Booked(id));
    }

    @Test
    void aSlotLostToARaceIsReportedAsOccupiedWithItsConflicts() {
        RoomOccupied raced = new RoomOccupied(List.of(
                new Conflict(DATE, new TimeSlot(LocalTime.of(18, 0), LocalTime.of(19, 0)))));
        BookRoomForEvent delegate = new StubBookRoomForEvent(null, raced);

        EventBookingResult result = new SpacesApiAdapter(delegate).requestForEvent(command());

        assertThat(result).isEqualTo(new Occupied(List.of(
                new EventBookingConflict(DATE, LocalTime.of(18, 0), LocalTime.of(19, 0)))));
    }

    @Test
    void theStatusQueryIsDelegated() {
        BookRoomForEvent delegate = new StubBookRoomForEvent(null, null);

        assertThat(new SpacesApiAdapter(delegate).bookingRequestStatus(UUID.randomUUID()))
                .contains(BookingRequestState.APPROVED);
    }

    private record StubBookRoomForEvent(EventBookingResult result, RoomOccupied failure) implements BookRoomForEvent {

        @Override
        public EventBookingResult requestForEvent(EventBookingCommand command) {
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        @Override
        public Optional<BookingRequestState> bookingRequestStatus(UUID bookingRequestId) {
            return Optional.of(BookingRequestState.APPROVED);
        }
    }
}
