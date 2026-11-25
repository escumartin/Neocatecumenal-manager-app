package io.github.escumartin.neocatecumenal.community.infrastructure.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(name = "MemberRecord")
@Table(name = "community_member")
class MemberJpaEntity {

    @Id
    UUID id;

    @Column(name = "community_id", nullable = false)
    UUID communityId;

    @Column(name = "given_name", nullable = false, length = 100)
    String givenName;

    @Column(length = 100)
    String surnames;

    @Column(length = 16)
    String mobile;

    @Column(length = 16)
    String landline;

    @Column(length = 254)
    String email;

    @Column(name = "user_id")
    UUID userId;

    @Version
    Long version;

    @OneToMany(cascade = CascadeType.ALL)
    @OrderBy("recordedAt ASC")
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    List<ConsentRecordJpaEntity> consents = new ArrayList<>();

    protected MemberJpaEntity() {
    }
}
