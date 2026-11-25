package io.github.escumartin.neocatecumenal.community.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.community.domain.CommunityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity(name = "CommunityRecord")
@Table(name = "community")
class CommunityJpaEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    int number;

    @Column(length = 100)
    String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    CommunityStatus status;

    @Column(name = "responsible_member_id")
    UUID responsibleMemberId;

    @Version
    Long version;

    protected CommunityJpaEntity() {
    }
}
