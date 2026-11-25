package io.github.escumartin.neocatecumenal.spaces.infrastructure.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room")
class RoomEntity {

    @Id
    UUID id;

    @Column(nullable = false, length = 80)
    String name;

    @Column(nullable = false)
    boolean active;

    @Version
    Long version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected RoomEntity() {
    }
}
