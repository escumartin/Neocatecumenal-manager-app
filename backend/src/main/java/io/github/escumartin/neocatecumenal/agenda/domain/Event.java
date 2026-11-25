package io.github.escumartin.neocatecumenal.agenda.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

public final class Event {

    private final EventId id;
    private final CommunityId communityId;
    private final EventType type;
    private final EventTitle title;
    private final LocalDate date;
    private final TimeSlot slot;
    private final RoomId roomId;
    private final BookingRequestId bookingRequestId;
    private final UserId createdBy;
    private final Instant createdAt;

    private Event(EventId id, CommunityId communityId, EventType type, EventTitle title, LocalDate date,
            TimeSlot slot, RoomId roomId, BookingRequestId bookingRequestId, UserId createdBy, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.communityId = Objects.requireNonNull(communityId);
        this.type = Objects.requireNonNull(type);
        this.title = Objects.requireNonNull(title);
        this.date = Objects.requireNonNull(date);
        this.slot = Objects.requireNonNull(slot);
        this.roomId = Objects.requireNonNull(roomId);
        this.bookingRequestId = Objects.requireNonNull(bookingRequestId);
        this.createdBy = createdBy;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Event create(EventId id, Actor actor, CommunityId communityId, EventType type, EventTitle title,
            LocalDate date, TimeSlot slot, RoomId roomId, BookingRequestId bookingRequestId, LocalDateTime now,
            Instant at) {
        requireStartInFuture(date, slot, now);
        return new Event(id, communityId, type, title, date, slot, roomId, bookingRequestId, actor.userId(), at);
    }

    public static Event restore(EventId id, CommunityId communityId, EventType type, EventTitle title,
            LocalDate date, TimeSlot slot, RoomId roomId, BookingRequestId bookingRequestId, UserId createdBy,
            Instant createdAt) {
        return new Event(id, communityId, type, title, date, slot, roomId, bookingRequestId, createdBy, createdAt);
    }

    public static void requireStartInFuture(LocalDate date, TimeSlot slot, LocalDateTime now) {
        if (!date.atTime(slot.start()).isAfter(now)) {
            throw new AgendaFieldInvalid("startTime", "START_NOT_IN_FUTURE");
        }
    }

    public EventId id() {
        return id;
    }

    public CommunityId communityId() {
        return communityId;
    }

    public EventType type() {
        return type;
    }

    public EventTitle title() {
        return title;
    }

    public LocalDate date() {
        return date;
    }

    public TimeSlot slot() {
        return slot;
    }

    public RoomId roomId() {
        return roomId;
    }

    public BookingRequestId bookingRequestId() {
        return bookingRequestId;
    }

    public Optional<UserId> createdBy() {
        return Optional.ofNullable(createdBy);
    }

    public Instant createdAt() {
        return createdAt;
    }
}
