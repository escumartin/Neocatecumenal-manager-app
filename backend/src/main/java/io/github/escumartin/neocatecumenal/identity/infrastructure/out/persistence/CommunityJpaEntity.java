package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "identity_community")
class CommunityJpaEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    int number;

    protected CommunityJpaEntity() {
    }
}
