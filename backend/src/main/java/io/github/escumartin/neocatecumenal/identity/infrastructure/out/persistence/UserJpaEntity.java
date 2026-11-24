package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_user")
class UserJpaEntity {

    @Id
    UUID id;

    @Column(name = "access_id", nullable = false)
    int accessId;

    @Column(nullable = false)
    String name;

    @Column(nullable = false)
    String role;

    @Column(name = "community_id", nullable = false)
    UUID communityId;

    @Column(name = "password_hash", nullable = false)
    String passwordHash;

    @Column(name = "password_change_required", nullable = false)
    boolean passwordChangeRequired;

    @Column(name = "privacy_notice_version")
    String privacyNoticeVersion;

    @Column(name = "privacy_notice_accepted_at")
    Instant privacyNoticeAcceptedAt;

    @Version
    Long version;

    protected UserJpaEntity() {
    }
}
