package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.ConsentChannel;
import io.github.escumartin.neocatecumenal.community.domain.ConsentContext;
import io.github.escumartin.neocatecumenal.community.domain.ConsentDecision;
import io.github.escumartin.neocatecumenal.community.domain.ConsentRecord;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange.FieldChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import io.github.escumartin.neocatecumenal.community.domain.RecorderRole;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class MemberPersistenceIT {

    private static final String LOCK_NOT_AVAILABLE = "55P03";
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");
    private static final UserId MODERATOR = new UserId(UUID.randomUUID());
    private static final ConsentContext VERBAL =
            new ConsentContext(MODERATOR, RecorderRole.MODERATOR, ConsentChannel.VERBAL, true);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private MemberRepository members;

    @Autowired
    private CommunityRepository communities;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Community community;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate community_contact_consent, community_member, community, identity_session, "
                + "identity_user, identity_community cascade");
        community = communities.save(Community.create(new CommunityId(UUID.randomUUID()), new CommunityNumber(1),
                Optional.empty()));
    }

    private Member newMember(String given, String surnames) {
        return Member.register(new MemberId(UUID.randomUUID()), community, PersonName.of(given, surnames),
                Optional.empty(), Optional.empty(), NOW);
    }

    private static ContactChange sharing(ContactField field, String value) {
        return ContactChange.of(Map.of(field, FieldChange.share(value)));
    }

    private static ContactChange withdrawing(ContactField field) {
        return ContactChange.of(Map.of(field, FieldChange.withdraw()));
    }

    private long versionOf(Member member) {
        return jdbc.queryForObject("select version from community_member where id = ?", Long.class,
                member.id().value());
    }

    private int rowsMentioning(String table, String text) {
        return jdbc.queryForObject("select count(*) from " + table + " t where t::text like ?", Integer.class,
                "%" + text + "%");
    }

    @Test
    void aMemberWithContactAndConsentsRoundTrips() {
        Member member = newMember("Ana", "Pérez");
        member.changeContact(ContactChange.of(Map.of(ContactField.MOBILE, FieldChange.share("600 000 001"),
                ContactField.EMAIL, FieldChange.share("ana@example.org"))), Optional.of(VERBAL), NOW);
        members.save(member);

        Member loaded = members.findById(member.id()).orElseThrow();

        assertThat(loaded.communityId()).isEqualTo(community.id());
        assertThat(loaded.name()).isEqualTo(PersonName.of("Ana", "Pérez"));
        assertThat(loaded.contact()).containsOnly(Map.entry(ContactField.MOBILE, "600000001"),
                Map.entry(ContactField.EMAIL, "ana@example.org"));
        assertThat(loaded.consents()).containsExactlyInAnyOrderElementsOf(member.consents());
        assertThat(loaded.linkedUserId()).isEmpty();
    }

    @Test
    void absentSurnamesAndContactAreStoredAsNull() {
        Member member = members.save(newMember("Ana", null));

        assertThat(jdbc.queryForMap("select surnames, mobile, landline, email, user_id from community_member "
                + "where id = ?", member.id().value())).containsOnlyKeys("surnames", "mobile", "landline", "email",
                        "user_id").containsEntry("surnames", null).containsEntry("mobile", null);
        assertThat(members.findById(member.id()).orElseThrow().name().surnames()).isEmpty();
    }

    @Test
    void theVersionIsBumpedOnEveryChange() {
        Member member = members.save(newMember("Ana", "Pérez"));
        long initial = versionOf(member);

        member.rename(PersonName.of("Beatriz", null));
        members.save(member);

        assertThat(versionOf(member)).isGreaterThan(initial);
        assertThat(members.findById(member.id()).orElseThrow().name().givenName()).isEqualTo("Beatriz");
    }

    @Test
    void consentRecordsAreInsertOnlyAndKeepTheirIdentity() {
        Member member = newMember("Ana", "Pérez");
        member.changeContact(sharing(ContactField.MOBILE, "600000001"), Optional.of(VERBAL), NOW);
        members.save(member);
        List<UUID> before = jdbc.queryForList("select id from community_contact_consent", UUID.class);

        member.changeContact(sharing(ContactField.EMAIL, "ana@example.org"), Optional.of(VERBAL), NOW);
        members.save(member);

        List<UUID> after = jdbc.queryForList("select id from community_contact_consent", UUID.class);
        assertThat(after).hasSize(2).containsAll(before);
    }

    @Test
    void consentRecordsStoreTheAuthorRoleChannelAndNoticeButNoContactValue() {
        Member member = newMember("Ana", "Pérez");
        member.changeContact(sharing(ContactField.MOBILE, "600000001"), Optional.of(VERBAL), NOW);
        members.save(member);

        Map<String, Object> row = jdbc.queryForMap("select * from community_contact_consent");

        assertThat(row).containsEntry("field", "MOBILE").containsEntry("decision", "GRANTED")
                .containsEntry("channel", "VERBAL").containsEntry("recorder_role", "MODERATOR")
                .containsEntry("notice_given", true).containsEntry("recorded_by_user_id", MODERATOR.value());
        assertThat(row.keySet()).doesNotContain("mobile", "landline", "email", "value");
        assertThat(rowsMentioning("community_contact_consent", "600000001")).isZero();
    }

    @Test
    void withdrawingLeavesTheValueNullAndAWithdrawnRecordWithoutValues() {
        Member member = newMember("Ana", "Pérez");
        member.changeContact(sharing(ContactField.MOBILE, "600000001"), Optional.of(VERBAL), NOW);
        members.save(member);

        member.changeContact(withdrawing(ContactField.MOBILE), Optional.of(VERBAL), NOW);
        members.save(member);

        assertThat(jdbc.queryForObject("select mobile from community_member where id = ?", String.class,
                member.id().value())).isNull();
        assertThat(jdbc.queryForList("select decision from community_contact_consent order by recorded_at, decision",
                String.class)).containsExactlyInAnyOrder("GRANTED", "WITHDRAWN");
        assertThat(rowsMentioning("community_member", "600000001")).isZero();
        assertThat(rowsMentioning("community_contact_consent", "600000001")).isZero();
        Member loaded = members.findById(member.id()).orElseThrow();
        assertThat(loaded.contact()).isEmpty();
        assertThat(loaded.consents()).extracting(ConsentRecord::decision)
                .containsExactlyInAnyOrder(ConsentDecision.GRANTED, ConsentDecision.WITHDRAWN);
    }

    @Test
    void aRectificationLeavesNoTraceOfThePreviousValueInAnyTable() {
        Member member = newMember("Ana", "Pérez");
        member.changeContact(sharing(ContactField.MOBILE, "600000001"), Optional.of(VERBAL), NOW);
        members.save(member);

        member.changeContact(sharing(ContactField.MOBILE, "600000002"), Optional.empty(), NOW);
        members.save(member);

        assertThat(rowsMentioning("community_member", "600000001")).isZero();
        assertThat(rowsMentioning("community_contact_consent", "600000001")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from community_contact_consent", Integer.class))
                .isEqualTo(1);
        assertThat(members.findById(member.id()).orElseThrow().contact())
                .containsEntry(ContactField.MOBILE, "600000002");
    }

    @Test
    void deletingTheMemberDeletesItsConsentRecordsInCascade() {
        Member member = newMember("Ana", "Pérez");
        member.changeContact(sharing(ContactField.MOBILE, "600000001"), Optional.of(VERBAL), NOW);
        members.save(member);

        jdbc.update("delete from community_member where id = ?", member.id().value());

        assertThat(jdbc.queryForObject("select count(*) from community_contact_consent", Integer.class)).isZero();
    }

    @Test
    void theSameUserCannotBeLinkedToTwoMembers() {
        UserId user = new UserId(UUID.randomUUID());
        members.save(Member.restore(new MemberId(UUID.randomUUID()), community.id(), PersonName.of("Ana", null),
                Map.of(), List.of(), Optional.of(user)));

        assertThatThrownBy(() -> members.save(Member.restore(new MemberId(UUID.randomUUID()), community.id(),
                PersonName.of("Luis", null), Map.of(), List.of(), Optional.of(user))))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("USER_ALREADY_LINKED"));
        assertThat(jdbc.queryForObject("select count(*) from community_member", Integer.class)).isEqualTo(1);
    }

    @Test
    void aMemberOfAMissingCommunityIsTranslatedToCommunityNotFound() {
        Member orphan = Member.restore(new MemberId(UUID.randomUUID()), new CommunityId(UUID.randomUUID()),
                PersonName.of("Ana", null), Map.of(), List.of(), Optional.empty());

        assertThatThrownBy(() -> members.save(orphan)).isInstanceOf(CommunityNotFound.class);
    }

    @Test
    void findByLinkedUserFindsTheMemberOrNothing() {
        UserId user = new UserId(UUID.randomUUID());
        Member linked = members.save(Member.restore(new MemberId(UUID.randomUUID()), community.id(),
                PersonName.of("Ana", null), Map.of(), List.of(), Optional.of(user)));

        assertThat(members.findByLinkedUser(user)).hasValueSatisfying(found -> assertThat(found.id())
                .isEqualTo(linked.id()));
        assertThat(members.findByLinkedUser(new UserId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void findByCommunityAndCountInOnlyConsiderThatCommunity() {
        Community another = communities.save(Community.create(new CommunityId(UUID.randomUUID()),
                new CommunityNumber(2), Optional.empty()));
        Member mine = members.save(newMember("Ana", "Pérez"));
        members.save(Member.register(new MemberId(UUID.randomUUID()), another, PersonName.of("Luis", null),
                Optional.empty(), Optional.empty(), NOW));

        assertThat(members.findByCommunity(community.id())).extracting(Member::id).containsExactly(mine.id());
        assertThat(members.countIn(community.id())).isEqualTo(1);
        assertThat(members.countIn(another.id())).isEqualTo(1);
    }

    @Test
    void findByIdOfAnUnknownMemberIsEmpty() {
        assertThat(members.findById(new MemberId(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void lockHoldsTheRowUntilTheTransactionEnds() {
        Member member = members.save(newMember("Ana", "Pérez"));

        Boolean blocked = new TransactionTemplate(transactionManager).execute(status -> {
            assertThat(members.lock(member.id())).isPresent();
            return rowIsLockedForOthers(member.id().value());
        });

        assertThat(blocked).isTrue();
        assertThat(rowIsLockedForOthers(member.id().value())).isFalse();
        assertThat(members.lock(new MemberId(UUID.randomUUID()))).isEmpty();
    }

    private boolean rowIsLockedForOthers(UUID id) {
        try (Connection probe = dataSource.getConnection();
                var statement = probe.prepareStatement(
                        "select id from community_member where id = ? for update nowait")) {
            statement.setObject(1, id);
            statement.executeQuery();
            return false;
        } catch (SQLException failure) {
            if (LOCK_NOT_AVAILABLE.equals(failure.getSQLState())) {
                return true;
            }
            throw new IllegalStateException(failure);
        }
    }
}
