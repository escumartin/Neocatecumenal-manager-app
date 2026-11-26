package io.github.escumartin.neocatecumenal.spaces.infrastructure.config;

import io.github.escumartin.neocatecumenal.spaces.SpacesApi;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.AnonymizeRequester;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.ApproveBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.BookRoomForEvent;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.CancelBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.CancelBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.CreateRoom;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.DeactivateRoom;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.GetBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.GetRoomOccupancy;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.ListBookingRequests;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.ListRooms;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.PurgeExpiredBookings;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.RectifyBookingReason;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.RejectBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.RenameRoom;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.RequestBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingCounter;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.BookingRequestRepository;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.CommunityLock;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomOccupancyQuery;
import io.github.escumartin.neocatecumenal.spaces.application.port.out.RoomRepository;
import io.github.escumartin.neocatecumenal.spaces.application.service.AnonymizeRequesterService;
import io.github.escumartin.neocatecumenal.spaces.application.service.ApproveBookingRequestService;
import io.github.escumartin.neocatecumenal.spaces.application.service.CancelBookingRequestService;
import io.github.escumartin.neocatecumenal.spaces.application.service.CancelBookingService;
import io.github.escumartin.neocatecumenal.spaces.application.service.CreateRoomService;
import io.github.escumartin.neocatecumenal.spaces.application.service.DeactivateRoomService;
import io.github.escumartin.neocatecumenal.spaces.application.service.GetBookingRequestService;
import io.github.escumartin.neocatecumenal.spaces.application.service.GetRoomOccupancyService;
import io.github.escumartin.neocatecumenal.spaces.application.service.ListBookingRequestsService;
import io.github.escumartin.neocatecumenal.spaces.application.service.ListRoomsService;
import io.github.escumartin.neocatecumenal.spaces.application.service.PurgeExpiredBookingsService;
import io.github.escumartin.neocatecumenal.spaces.application.service.RectifyBookingReasonService;
import io.github.escumartin.neocatecumenal.spaces.application.service.RejectBookingRequestService;
import io.github.escumartin.neocatecumenal.spaces.application.service.RenameRoomService;
import io.github.escumartin.neocatecumenal.spaces.application.service.RequestBookingService;
import io.github.escumartin.neocatecumenal.spaces.application.service.RequestForEventService;
import io.github.escumartin.neocatecumenal.spaces.application.service.SpacesApiAdapter;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpacesConfig {

    @Bean
    SpacesServiceClock spacesServiceClock(Clock clock) {
        return new SpacesServiceClock(clock.withZone(ZoneId.of("Europe/Madrid")));
    }

    @Bean
    ListRooms listRooms(RoomRepository rooms) {
        return new ListRoomsService(rooms);
    }

    @Bean
    CreateRoom createRoom(RoomRepository rooms) {
        return new CreateRoomService(rooms);
    }

    @Bean
    RenameRoom renameRoom(RoomRepository rooms) {
        return new RenameRoomService(rooms);
    }

    @Bean
    DeactivateRoom deactivateRoom(RoomRepository rooms, BookingCounter bookings, SpacesServiceClock clock) {
        return new DeactivateRoomService(rooms, bookings, clock.clock());
    }

    @Bean
    RequestBooking requestBooking(RoomRepository rooms, BookingRequestRepository requests,
            CommunityLock communities, SpacesServiceClock clock) {
        return new RequestBookingService(rooms, requests, communities, clock.clock());
    }

    @Bean
    RectifyBookingReason rectifyBookingReason(BookingRequestRepository requests) {
        return new RectifyBookingReasonService(requests);
    }

    @Bean
    ApproveBookingRequest approveBookingRequest(BookingRequestRepository requests, SpacesServiceClock clock) {
        return new ApproveBookingRequestService(requests, clock.clock());
    }

    @Bean
    RejectBookingRequest rejectBookingRequest(BookingRequestRepository requests, SpacesServiceClock clock) {
        return new RejectBookingRequestService(requests, clock.clock());
    }

    @Bean
    CancelBookingRequest cancelBookingRequest(BookingRequestRepository requests, SpacesServiceClock clock) {
        return new CancelBookingRequestService(requests, clock.clock());
    }

    @Bean
    CancelBooking cancelBooking(BookingRequestRepository requests, SpacesServiceClock clock) {
        return new CancelBookingService(requests, clock.clock());
    }

    @Bean
    ListBookingRequests listBookingRequests(BookingRequestRepository requests) {
        return new ListBookingRequestsService(requests);
    }

    @Bean
    GetBookingRequest getBookingRequest(BookingRequestRepository requests) {
        return new GetBookingRequestService(requests);
    }

    @Bean
    GetRoomOccupancy getRoomOccupancy(RoomRepository rooms, RoomOccupancyQuery occupancy) {
        return new GetRoomOccupancyService(rooms, occupancy);
    }

    @Bean
    BookRoomForEvent bookRoomForEvent(RoomRepository rooms, BookingRequestRepository requests,
            SpacesServiceClock clock) {
        return new RequestForEventService(rooms, requests, clock.clock());
    }

    @Bean
    SpacesApi spacesApi(BookRoomForEvent bookRoomForEvent, BookingRequestRepository requests) {
        return new SpacesApiAdapter(bookRoomForEvent, requests);
    }

    @Bean
    AnonymizeRequester anonymizeRequester(BookingRequestRepository requests) {
        return new AnonymizeRequesterService(requests);
    }

    @Bean
    PurgeExpiredBookings purgeExpiredBookings(BookingRequestRepository requests, SpacesServiceClock clock) {
        return new PurgeExpiredBookingsService(requests, clock.clock());
    }
}
