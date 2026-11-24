package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_session")
class SessionJpaEntity {

    @Id
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "token_hash", nullable = false)
    byte[] tokenHash;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    Instant lastUsedAt;

    protected SessionJpaEntity() {
    }
}
