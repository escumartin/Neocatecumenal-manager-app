package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class ResponsiblePersistenceIT {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private CommunityRepository communities;

    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    private Community own;
    private Community other;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate community_contact_consent, community_member, community, identity_session, "
                + "identity_user, identity_community cascade");
        own = communities.save(Community.create(new CommunityId(UUID.randomUUID()), new CommunityNumber(1),
                Optional.empty()));
        other = communities.save(Community.create(new CommunityId(UUID.randomUUID()), new CommunityNumber(2),
                Optional.empty()));
    }

    private Member memberIn(Community community, String given) {
        return members.save(Member.register(new MemberId(UUID.randomUUID()), community,
                PersonName.of(given, null), Optional.empty(), Optional.empty(), NOW));
    }

    private Object storedResponsible(Community community) {
        return jdbc.queryForObject("select responsible_member_id from community where id = ?", Object.class,
                community.id().value());
    }

    @Test
    void theResponsibleOfAMemberOfTheCommunityIsPersistedAndRestored() {
        Member member = memberIn(own, "Ana");
        own.designateResponsible(member);

        communities.save(own);

        assertThat(communities.findById(own.id())).hasValueSatisfying(
                restored -> assertThat(restored.isResponsible(member.id())).isTrue());
    }

    @Test
    void savingAResponsibleOfAnotherCommunityViolatesTheCompositeForeignKey() {
        Member foreign = memberIn(other, "Carla");

        assertThatThrownBy(() -> jdbc.update("update community set responsible_member_id = ? where id = ?",
                foreign.id().value(), own.id().value()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_community_responsible");
        assertThat(storedResponsible(own)).isNull();
    }

    @Test
    void updatingTheResponsibleByHandToAForeignMemberKeepsTheCurrentOne() {
        Member current = memberIn(own, "Ana");
        Member foreign = memberIn(other, "Carla");
        own.designateResponsible(current);
        communities.save(own);

        assertThatThrownBy(() -> jdbc.update("update community set responsible_member_id = ? where id = ?",
                foreign.id().value(), own.id().value())).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(storedResponsible(own)).isEqualTo(current.id().value());
    }

    @Test
    void deletingTheResponsibleMemberViolatesTheForeignKey() {
        Member responsible = memberIn(own, "Ana");
        own.designateResponsible(responsible);
        communities.save(own);

        assertThatThrownBy(() -> jdbc.update("delete from community_member where id = ?",
                responsible.id().value())).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_community_responsible");
    }

    @Test
    void movingTheResponsibleMemberToAnotherCommunityViolatesTheForeignKey() {
        Member responsible = memberIn(own, "Ana");
        own.designateResponsible(responsible);
        communities.save(own);

        assertThatThrownBy(() -> jdbc.update("update community_member set community_id = ? where id = ?",
                other.id().value(), responsible.id().value())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aMemberWhoIsNotTheResponsibleCanBeDeleted() {
        Member responsible = memberIn(own, "Ana");
        Member plain = memberIn(own, "Berta");
        own.designateResponsible(responsible);
        communities.save(own);

        int deleted = jdbc.update("delete from community_member where id = ?", plain.id().value());

        assertThat(deleted).isEqualTo(1);
    }
}
