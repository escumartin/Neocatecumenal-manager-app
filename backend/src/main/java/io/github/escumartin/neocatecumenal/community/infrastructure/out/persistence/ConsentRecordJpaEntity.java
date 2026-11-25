package io.github.escumartin.neocatecumenal.community.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.community.domain.ConsentChannel;
import io.github.escumartin.neocatecumenal.community.domain.ConsentDecision;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.RecorderRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity(name = "ContactConsentRecord")
@Table(name = "community_contact_consent")
@Immutable
class ConsentRecordJpaEntity {

    @Id
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, length = 16)
    ContactField field;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    ConsentDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    ConsentChannel channel;

    @Column(name = "recorded_by_user_id", nullable = false)
    UUID recordedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recorder_role", nullable = false, length = 16)
    RecorderRole recorderRole;

    @Column(name = "notice_given", nullable = false)
    boolean noticeGiven;

    @Column(name = "recorded_at", nullable = false)
    Instant recordedAt;

    protected ConsentRecordJpaEntity() {
    }
}
