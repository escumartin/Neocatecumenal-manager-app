package io.github.escumartin.neocatecumenal.spaces.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.api.RoomsApi;
import io.github.escumartin.neocatecumenal.generated.model.Room;
import io.github.escumartin.neocatecumenal.generated.model.RoomInput;
import io.github.escumartin.neocatecumenal.generated.model.RoomOccupancy;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.CreateRoom;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.DeactivateRoom;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.GetRoomOccupancy;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.ListRooms;
import io.github.escumartin.neocatecumenal.spaces.application.port.in.RenameRoom;
import io.github.escumartin.neocatecumenal.spaces.domain.RoomId;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RoomsController implements RoomsApi {

    private final ActorResolver actors;
    private final ListRooms listRooms;
    private final CreateRoom createRoom;
    private final RenameRoom renameRoom;
    private final DeactivateRoom deactivateRoom;
    private final GetRoomOccupancy getRoomOccupancy;

    RoomsController(ActorResolver actors, ListRooms listRooms, CreateRoom createRoom, RenameRoom renameRoom,
            DeactivateRoom deactivateRoom, GetRoomOccupancy getRoomOccupancy) {
        this.actors = actors;
        this.listRooms = listRooms;
        this.createRoom = createRoom;
        this.renameRoom = renameRoom;
        this.deactivateRoom = deactivateRoom;
        this.getRoomOccupancy = getRoomOccupancy;
    }

    @Override
    public ResponseEntity<List<Room>> listRooms() {
        actors.currentActor();
        return ResponseEntity.ok(listRooms.list().stream().map(RoomsMapper::toResponse).toList());
    }

    @Override
    public ResponseEntity<Room> createRoom(RoomInput roomInput) {
        var created = createRoom.create(actors.currentActor(), RoomsMapper.toName(roomInput.getName()));
        return ResponseEntity.created(URI.create("/api/rooms/" + created.id().value()))
                .body(RoomsMapper.toResponse(created));
    }

    @Override
    public ResponseEntity<Room> renameRoom(UUID roomId, RoomInput roomInput) {
        var renamed = renameRoom.rename(actors.currentActor(), new RoomId(roomId),
                RoomsMapper.toName(roomInput.getName()));
        return ResponseEntity.ok(RoomsMapper.toResponse(renamed));
    }

    @Override
    public ResponseEntity<Void> deactivateRoom(UUID roomId) {
        deactivateRoom.deactivate(actors.currentActor(), new RoomId(roomId));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<RoomOccupancy> getRoomOccupancy(UUID roomId, LocalDate date, UUID communityId) {
        var actor = actors.currentActor();
        var requested = actors.requestedCommunity(actor, communityId).orElse(null);
        return ResponseEntity.ok(RoomsMapper.toResponse(
                getRoomOccupancy.get(actor, new RoomId(roomId), date, requested)));
    }
}
