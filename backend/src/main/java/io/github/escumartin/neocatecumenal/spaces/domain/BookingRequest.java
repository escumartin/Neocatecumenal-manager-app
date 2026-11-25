package io.github.escumartin.neocatecumenal.spaces.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BookingRequest {

    private final BookingRequestId id;
    private final RoomId roomId;
    private final CommunityId communityId;
    private final UserId requestedBy;
    private final EventId eventId;
    private Reason reason;
    private RejectionReason rejectionReason;
    private final LocalDate firstDate;
    private final LocalDate lastDate;
    private final TimeSlot slot;
    private final WeeklyRecurrence recurrence;
    private BookingRequestStatus status;
    private final List<Booking> bookings;
    private final List<StatusChange> history;

    private BookingRequest(BookingRequestId id, RoomId roomId, CommunityId communityId, UserId requestedBy,
            EventId eventId, Reason reason, RejectionReason rejectionReason, LocalDate firstDate, LocalDate lastDate,
            TimeSlot slot, WeeklyRecurrence recurrence, BookingRequestStatus status, List<Booking> bookings,
            List<StatusChange> history) {
        this.id = Objects.requireNonNull(id);
        this.roomId = Objects.requireNonNull(roomId);
        this.communityId = Objects.requireNonNull(communityId);
        this.requestedBy = requestedBy;
        this.eventId = eventId;
        this.reason = Objects.requireNonNull(reason);
        this.rejectionReason = rejectionReason;
        this.firstDate = Objects.requireNonNull(firstDate);
        this.lastDate = Objects.requireNonNull(lastDate);
        this.slot = Objects.requireNonNull(slot);
        this.recurrence = recurrence;
        this.status = Objects.requireNonNull(status);
        this.bookings = new ArrayList<>(bookings);
        this.history = new ArrayList<>(history);
    }

    public static BookingRequest request(Actor actor, RoomId roomId, CommunityId communityId, LocalDate date,
            TimeSlot slot, Reason reason, LocalDateTime now, Instant at) {
        return request(actor, roomId, communityId, date, slot, reason, null, now, at);
    }

    public static BookingRequest request(Actor actor, RoomId roomId, CommunityId communityId, LocalDate date,
            TimeSlot slot, Reason reason, WeeklyRecurrence recurrence, LocalDateTime now, Instant at) {
        return create(actor.userId(), roomId, communityId, date, slot, reason, recurrence, null, now, at);
    }

    public static BookingRequest forEvent(UserId requestedBy, CommunityId communityId, RoomId roomId, LocalDate date,
            TimeSlot slot, Reason reason, EventId eventId, LocalDateTime now, Instant at) {
        return create(requestedBy, roomId, communityId, date, slot, reason, null, eventId, now, at);
    }

    private static BookingRequest create(UserId requestedBy, RoomId roomId, CommunityId communityId, LocalDate date,
            TimeSlot slot, Reason reason, WeeklyRecurrence recurrence, EventId eventId, LocalDateTime now,
            Instant at) {
        if (!date.atTime(slot.start()).isAfter(now)) {
            throw new SpacesFieldInvalid("startTime", "START_NOT_IN_FUTURE");
        }
        List<LocalDate> dates = recurrence == null ? List.of(date) : recurrence.dates(date);
        List<Booking> occurrences = dates.stream()
                .map(occurrence -> new Booking(new BookingId(UUID.randomUUID()), occurrence, slot,
                        BookingStatus.PENDING))
                .toList();
        StatusChange created = new StatusChange(null, null, BookingRequestStatus.PENDING, requestedBy, at);
        return new BookingRequest(new BookingRequestId(UUID.randomUUID()), roomId, communityId, requestedBy,
                eventId, reason, null, date, dates.get(dates.size() - 1), slot, recurrence, BookingRequestStatus.PENDING,
                occurrences, List.of(created));
    }

    public static BookingRequest restore(BookingRequestId id, RoomId roomId, CommunityId communityId,
            UserId requestedBy, Reason reason, RejectionReason rejectionReason, LocalDate firstDate,
            LocalDate lastDate, TimeSlot slot, BookingRequestStatus status, List<Booking> bookings,
            List<StatusChange> history) {
        return restore(id, roomId, communityId, requestedBy, reason, rejectionReason, firstDate, lastDate, slot,
                null, status, bookings, history);
    }

    public static BookingRequest restore(BookingRequestId id, RoomId roomId, CommunityId communityId,
            UserId requestedBy, Reason reason, RejectionReason rejectionReason, LocalDate firstDate,
            LocalDate lastDate, TimeSlot slot, WeeklyRecurrence recurrence, BookingRequestStatus status,
            List<Booking> bookings, List<StatusChange> history) {
        return restore(id, roomId, communityId, requestedBy, reason, rejectionReason, firstDate, lastDate, slot,
                recurrence, null, status, bookings, history);
    }

    public static BookingRequest restore(BookingRequestId id, RoomId roomId, CommunityId communityId,
            UserId requestedBy, Reason reason, RejectionReason rejectionReason, LocalDate firstDate,
            LocalDate lastDate, TimeSlot slot, WeeklyRecurrence recurrence, EventId eventId,
            BookingRequestStatus status, List<Booking> bookings, List<StatusChange> history) {
        return new BookingRequest(id, roomId, communityId, requestedBy, eventId, reason, rejectionReason, firstDate,
                lastDate, slot, recurrence, status, bookings, history);
    }

    public void rectifyReason(Actor actor, Reason newReason) {
        BookingPolicy.requireAccessTo(actor, communityId);
        if (status != BookingRequestStatus.PENDING) {
            throw new BookingRequestNotPending();
        }
        this.reason = Objects.requireNonNull(newReason);
    }

    public void approve(Actor actor, LocalDateTime now, Instant at) {
        BookingPolicy.requireAdministrator(actor);
        requirePending();
        if (!firstDate.atTime(slot.start()).isAfter(now)) {
            throw new SpacesFieldInvalid("startTime", "START_NOT_IN_FUTURE");
        }
        resolve(actor, BookingRequestStatus.APPROVED, BookingStatus.APPROVED, at);
    }

    public void reject(Actor actor, RejectionReason reason, Instant at) {
        BookingPolicy.requireAdministrator(actor);
        requirePending();
        resolve(actor, BookingRequestStatus.REJECTED, BookingStatus.REJECTED, at);
        this.rejectionReason = reason;
    }

    public void cancel(Actor actor, LocalDateTime now, Instant at) {
        BookingPolicy.requireAccessTo(actor, communityId);
        requireActive();
        List<Booking> cancellable = bookings.stream()
                .filter(booking -> booking.status().active() && !booking.hasStarted(now)).toList();
        if (cancellable.isEmpty()) {
            throw new BookingAlreadyStarted();
        }
        bookings.replaceAll(booking -> cancellable.contains(booking) ? booking.withStatus(BookingStatus.CANCELLED)
                : booking);
        cancelRequest(actor, at);
    }

    public void cancelBooking(Actor actor, BookingId bookingId, LocalDateTime now, Instant at) {
        BookingPolicy.requireAccessTo(actor, communityId);
        requireActive();
        Booking target = bookings.stream().filter(booking -> booking.id().equals(bookingId)).findFirst()
                .orElseThrow(BookingRequestNotFound::new);
        if (!target.status().active()) {
            throw new BookingRequestNotActive();
        }
        if (target.hasStarted(now)) {
            throw new BookingAlreadyStarted();
        }
        bookings.replaceAll(booking -> booking.equals(target) ? booking.withStatus(BookingStatus.CANCELLED) : booking);
        history.add(new StatusChange(bookingId, BookingRequestStatus.valueOf(target.status().name()),
                BookingRequestStatus.CANCELLED, actor.userId(), at));
        if (bookings.stream().noneMatch(booking -> booking.status().active())) {
            cancelRequest(actor, at);
        }
    }

    private void requireActive() {
        if (status != BookingRequestStatus.PENDING && status != BookingRequestStatus.APPROVED) {
            throw new BookingRequestNotActive();
        }
    }

    private void cancelRequest(Actor actor, Instant at) {
        history.add(new StatusChange(null, status, BookingRequestStatus.CANCELLED, actor.userId(), at));
        status = BookingRequestStatus.CANCELLED;
    }

    private void requirePending() {
        if (status != BookingRequestStatus.PENDING) {
            throw new BookingRequestNotPending();
        }
    }

    private void resolve(Actor actor, BookingRequestStatus target, BookingStatus occurrenceTarget, Instant at) {
        bookings.replaceAll(booking -> booking.status() == BookingStatus.PENDING
                ? new Booking(booking.id(), booking.date(), booking.slot(), occurrenceTarget)
                : booking);
        history.add(new StatusChange(null, status, target, actor.userId(), at));
        status = target;
    }

    public BookingRequestId id() {
        return id;
    }

    public RoomId roomId() {
        return roomId;
    }

    public CommunityId communityId() {
        return communityId;
    }

    public Optional<UserId> requestedBy() {
        return Optional.ofNullable(requestedBy);
    }

    public Optional<EventId> eventId() {
        return Optional.ofNullable(eventId);
    }

    public Reason reason() {
        return reason;
    }

    public Optional<RejectionReason> rejectionReason() {
        return Optional.ofNullable(rejectionReason);
    }

    public LocalDate firstDate() {
        return firstDate;
    }

    public LocalDate lastDate() {
        return lastDate;
    }

    public TimeSlot slot() {
        return slot;
    }

    public Optional<WeeklyRecurrence> recurrence() {
        return Optional.ofNullable(recurrence);
    }

    public BookingRequestStatus status() {
        return status;
    }

    public List<Booking> bookings() {
        return List.copyOf(bookings);
    }

    public List<StatusChange> history() {
        return List.copyOf(history);
    }

    public Instant createdAt() {
        return history.get(0).changedAt();
    }
}
