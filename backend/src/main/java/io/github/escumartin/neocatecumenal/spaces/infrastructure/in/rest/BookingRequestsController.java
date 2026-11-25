package io.github.escumartin.neocatecumenal.spaces.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.api.BookingRequestsApi;
import io.github.escumartin.neocatecumenal.generated.model.BookingReasonInput;
import io.github.escumartin.neocatecumenal.generated.model.BookingRequest;
import io.github.escumartin.neocatecumenal.generated.model.BookingRequestDetail;
import io.github.escumartin.neocatecumenal.generated.model.BookingRequestPage;
import io.github.escumartin.neocatecumenal.generated.model.BookingRequestStatus;
import io.github.escumartin.neocatecumenal.generated.model.CreateBookingRequest;
import io.github.escumartin.neocatecumenal.generated.model.RejectBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.ApproveBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.CancelBooking;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.CancelBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.GetBookingRequest;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.ListBookingRequests;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.RectifyBookingReason;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.RequestBooking;
import io.github.escumartin.neocatecumenal.spaces.domain.Actor;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingId;
import io.github.escumartin.neocatecumenal.spaces.domain.BookingRequestId;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class BookingRequestsController implements BookingRequestsApi {

    private final ActorResolver actors;
    private final RequestBooking requestBooking;
    private final RectifyBookingReason rectifyReason;
    private final ListBookingRequests listRequests;
    private final GetBookingRequest getRequest;
    private final ApproveBookingRequest approveRequest;
    private final CancelBookingRequest cancelRequest;
    private final CancelBooking cancelOccurrence;
    private final io.github.escumartin.neocatecumenal.spaces.application.port.in.RejectBookingRequest rejectRequest;

    BookingRequestsController(ActorResolver actors, RequestBooking requestBooking,
            RectifyBookingReason rectifyReason, ListBookingRequests listRequests, GetBookingRequest getRequest,
            ApproveBookingRequest approveRequest, CancelBookingRequest cancelRequest,
            CancelBooking cancelOccurrence,
            io.github.escumartin.neocatecumenal.spaces.application.port.in.RejectBookingRequest rejectRequest) {
        this.actors = actors;
        this.requestBooking = requestBooking;
        this.rectifyReason = rectifyReason;
        this.listRequests = listRequests;
        this.getRequest = getRequest;
        this.approveRequest = approveRequest;
        this.cancelRequest = cancelRequest;
        this.cancelOccurrence = cancelOccurrence;
        this.rejectRequest = rejectRequest;
    }

    @Override
    public ResponseEntity<BookingRequest> createBookingRequest(CreateBookingRequest body) {
        Actor actor = actors.currentActor();
        CommunityId community = actors.requestedCommunity(actor, body.getCommunityId()).orElse(null);
        var created = requestBooking.request(actor, new RoomId(body.getRoomId()), community, body.getDate(),
                BookingRequestsMapper.toSlot(body.getStartTime(), body.getEndTime()),
                BookingRequestsMapper.toReason(body.getReason()),
                BookingRequestsMapper.toRecurrence(body.getRecurrence()));
        return ResponseEntity.created(URI.create("/api/booking-requests/" + created.id().value()))
                .body(BookingRequestsMapper.toResponse(created));
    }

    @Override
    public ResponseEntity<BookingRequestPage> listBookingRequests(List<BookingRequestStatus> status,
            UUID communityId, Integer page, Integer size) {
        Actor actor = actors.currentActor();
        CommunityId community = actors.requestedCommunity(actor, communityId).orElse(null);
        var found = listRequests.list(actor, community, BookingRequestsMapper.toStatuses(status), page, size);
        return ResponseEntity.ok(BookingRequestsMapper.toPage(found));
    }

    @Override
    public ResponseEntity<BookingRequestDetail> getBookingRequest(UUID bookingRequestId) {
        var found = getRequest.get(actors.currentActor(), new BookingRequestId(bookingRequestId));
        return ResponseEntity.ok(BookingRequestsMapper.toDetail(found));
    }

    @Override
    public ResponseEntity<BookingRequest> rectifyBookingRequestReason(UUID bookingRequestId,
            BookingReasonInput input) {
        var rectified = rectifyReason.rectify(actors.currentActor(), new BookingRequestId(bookingRequestId),
                BookingRequestsMapper.toReason(input.getReason()));
        return ResponseEntity.ok(BookingRequestsMapper.toResponse(rectified));
    }

    @Override
    public ResponseEntity<BookingRequest> approveBookingRequest(UUID bookingRequestId) {
        var approved = approveRequest.approve(actors.currentActor(), new BookingRequestId(bookingRequestId));
        return ResponseEntity.ok(BookingRequestsMapper.toResponse(approved));
    }

    @Override
    public ResponseEntity<BookingRequestDetail> cancelBooking(UUID bookingRequestId, UUID bookingId) {
        var cancelled = cancelOccurrence.cancel(actors.currentActor(), new BookingRequestId(bookingRequestId),
                new BookingId(bookingId));
        return ResponseEntity.ok(BookingRequestsMapper.toDetail(cancelled));
    }

    @Override
    public ResponseEntity<BookingRequest> cancelBookingRequest(UUID bookingRequestId) {
        var cancelled = cancelRequest.cancel(actors.currentActor(), new BookingRequestId(bookingRequestId));
        return ResponseEntity.ok(BookingRequestsMapper.toResponse(cancelled));
    }

    @Override
    public ResponseEntity<BookingRequest> rejectBookingRequest(UUID bookingRequestId,
            RejectBookingRequest rejectBookingRequest) {
        var rejected = rejectRequest.reject(actors.currentActor(), new BookingRequestId(bookingRequestId),
                BookingRequestsMapper.toRejectionReason(rejectBookingRequest));
        return ResponseEntity.ok(BookingRequestsMapper.toResponse(rejected));
    }
}
