package io.github.escumartin.neocatecumenal.agenda.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.agenda.application.port.in.EventDetails;
import io.github.escumartin.neocatecumenal.agenda.domain.EventTitle;
import io.github.escumartin.neocatecumenal.agenda.domain.TimeSlot;
import io.github.escumartin.neocatecumenal.generated.model.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.generated.model.Event;
import io.github.escumartin.neocatecumenal.generated.model.EventType;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

final class EventsMapper {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private EventsMapper() {
    }

    static EventTitle toTitle(String title) {
        return new EventTitle(title);
    }

    static TimeSlot toSlot(String startTime, String endTime) {
        return new TimeSlot(LocalTime.parse(startTime), LocalTime.parse(endTime));
    }

    static io.github.escumartin.neocatecumenal.agenda.domain.EventType toType(EventType type) {
        return io.github.escumartin.neocatecumenal.agenda.domain.EventType.valueOf(type.getValue());
    }

    static Event toResponse(EventDetails details) {
        var event = details.event();
        return new Event(event.id().value(), EventType.fromValue(event.type().name()), event.title().value(),
                event.communityId().value(), event.roomId().value(), event.date(), time(event.slot().start()),
                time(event.slot().end()), event.bookingRequestId().value(),
                BookingRequestStatus.fromValue(details.bookingStatus().name()));
    }

    private static String time(LocalTime time) {
        return TIME.format(time);
    }
}
