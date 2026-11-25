package io.github.escumartin.neocatecumenal.community.infrastructure;

import static io.github.escumartin.neocatecumenal.FlywayTestSupport.assertSqlState;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class CommunitySchemaMigrationIT {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void freshSchema() {
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        migrateTo("6");
    }

    private static void migrateTo(String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load()
                .migrate();
    }

    private UUID insertCommunity(int number) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into community (id, number, status, version) values (?, ?, 'ACTIVE', 0)", id, number);
        return id;
    }

    private UUID insertMember(UUID communityId, String givenName) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into community_member (id, community_id, given_name, version) values (?, ?, ?, 0)", id,
                communityId, givenName);
        return id;
    }

    private void insertConsent(UUID memberId, String channel, String recorderRole, boolean noticeGiven) {
        jdbc.update("""
                insert into community_contact_consent (id, member_id, field, decision, channel, recorded_by_user_id,
                    recorder_role, notice_given, recorded_at)
                values (?, ?, 'MOBILE', 'GRANTED', ?, ?, ?, ?, now())
                """, UUID.randomUUID(), memberId, channel, UUID.randomUUID(), recorderRole, noticeGiven);
    }

    @Test
    void communityNumbersAreUnique() {
        insertCommunity(5);

        assertSqlState(() -> insertCommunity(5), UNIQUE_VIOLATION);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3})
    void communityNumbersMustBePositive(int number) {
        assertSqlState(() -> insertCommunity(number), CHECK_VIOLATION);
    }

    @Test
    void aBlankCommunityNameIsRejected() {
        assertSqlState(() -> jdbc.update(
                "insert into community (id, number, name, status, version) values (?, 7, '   ', 'ACTIVE', 0)",
                UUID.randomUUID()), CHECK_VIOLATION);
    }

    @Test
    void aMissingCommunityNameIsAccepted() {
        insertCommunity(8);

        assertThat(jdbc.queryForObject("select count(*) from community where name is null", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void communityStatusMustBeKnown() {
        assertSqlState(() -> jdbc.update(
                "insert into community (id, number, status, version) values (?, 9, 'ARCHIVED', 0)", UUID.randomUUID()),
                CHECK_VIOLATION);
    }

    @Test
    void theResponsibleMustBelongToTheCommunity() {
        UUID community = insertCommunity(1);
        UUID other = insertCommunity(2);
        UUID outsider = insertMember(other, "Test Person");

        assertSqlState(() -> jdbc.update("update community set responsible_member_id = ? where id = ?", outsider,
                community), FOREIGN_KEY_VIOLATION);
    }

    @Test
    void aMemberOfTheCommunityCanBeItsResponsible() {
        UUID community = insertCommunity(1);
        UUID member = insertMember(community, "Test Person");

        jdbc.update("update community set responsible_member_id = ? where id = ?", member, community);

        assertThat(jdbc.queryForObject("select responsible_member_id from community where id = ?", UUID.class,
                community)).isEqualTo(member);
    }

    @Test
    void aResponsibleMemberCannotBeMovedToAnotherCommunityByHand() {
        UUID community = insertCommunity(1);
        UUID other = insertCommunity(2);
        UUID member = insertMember(community, "Test Person");
        jdbc.update("update community set responsible_member_id = ? where id = ?", member, community);

        assertSqlState(() -> jdbc.update("update community_member set community_id = ? where id = ?", other, member),
                FOREIGN_KEY_VIOLATION);
    }

    @Test
    void aCommunityWithMembersCannotBeDeleted() {
        UUID community = insertCommunity(1);
        insertMember(community, "Test Person");

        assertSqlState(() -> jdbc.update("delete from community where id = ?", community), FOREIGN_KEY_VIOLATION);
    }

    @Test
    void aMemberNeedsAGivenName() {
        UUID community = insertCommunity(1);

        assertSqlState(() -> insertMember(community, "  "), CHECK_VIOLATION);
    }

    @Test
    void blankSurnamesAreRejected() {
        UUID community = insertCommunity(1);

        assertSqlState(() -> jdbc.update("""
                insert into community_member (id, community_id, given_name, surnames, version)
                values (?, ?, 'Test', ' ', 0)
                """, UUID.randomUUID(), community), CHECK_VIOLATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"600000001", "+351912345678", "912345678"})
    void validPhoneNumbersAreAccepted(String phone) {
        UUID community = insertCommunity(1);

        jdbc.update("""
                insert into community_member (id, community_id, given_name, mobile, landline, version)
                values (?, ?, 'Test', ?, ?, 0)
                """, UUID.randomUUID(), community, phone, phone);

        assertThat(jdbc.queryForObject("select count(*) from community_member where mobile = ?", Integer.class,
                phone)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"512345678", "60000000", "+12345678901234a", "6000000012", "abc"})
    void invalidMobileNumbersAreRejected(String phone) {
        UUID community = insertCommunity(1);

        assertSqlState(() -> jdbc.update("""
                insert into community_member (id, community_id, given_name, mobile, version)
                values (?, ?, 'Test', ?, 0)
                """, UUID.randomUUID(), community, phone), CHECK_VIOLATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"512345678", "60000000", "+12345678901234a"})
    void invalidLandlineNumbersAreRejected(String phone) {
        UUID community = insertCommunity(1);

        assertSqlState(() -> jdbc.update("""
                insert into community_member (id, community_id, given_name, landline, version)
                values (?, ?, 'Test', ?, 0)
                """, UUID.randomUUID(), community, phone), CHECK_VIOLATION);
    }

    @Test
    void aPhoneNumberLongerThanTheColumnIsRejected() {
        UUID community = insertCommunity(1);

        assertSqlState(() -> jdbc.update("""
                insert into community_member (id, community_id, given_name, mobile, version)
                values (?, ?, 'Test', '+1234567890123456', 0)
                """, UUID.randomUUID(), community), "22001");
    }

    @Test
    void aUserLinksToAtMostOneMember() {
        UUID community = insertCommunity(1);
        UUID user = UUID.randomUUID();
        jdbc.update("insert into community_member (id, community_id, given_name, user_id, version) "
                + "values (?, ?, 'Test', ?, 0)", UUID.randomUUID(), community, user);

        assertSqlState(() -> jdbc.update("insert into community_member (id, community_id, given_name, user_id, "
                + "version) values (?, ?, 'Other', ?, 0)", UUID.randomUUID(), community, user), UNIQUE_VIOLATION);
    }

    @Test
    void membersWithoutUserDoNotCollide() {
        UUID community = insertCommunity(1);

        insertMember(community, "First");
        insertMember(community, "Second");

        assertThat(jdbc.queryForObject("select count(*) from community_member", Integer.class)).isEqualTo(2);
    }

    @Test
    void consentIsDeletedWithItsMember() {
        UUID community = insertCommunity(1);
        UUID member = insertMember(community, "Test Person");
        insertConsent(member, "APP", "MEMBER", false);

        jdbc.update("delete from community_member where id = ?", member);

        assertThat(jdbc.queryForObject("select count(*) from community_contact_consent", Integer.class)).isZero();
    }

    @Test
    void anAppConsentMustBeRecordedByTheMember() {
        UUID community = insertCommunity(1);
        UUID member = insertMember(community, "Test Person");

        assertSqlState(() -> insertConsent(member, "APP", "MODERATOR", false), CHECK_VIOLATION);
    }

    @Test
    void aMemberRecordedConsentMustComeFromTheApp() {
        UUID community = insertCommunity(1);
        UUID member = insertMember(community, "Test Person");

        assertSqlState(() -> insertConsent(member, "VERBAL", "MEMBER", true), CHECK_VIOLATION);
    }

    @Test
    void anOnBehalfConsentRequiresTheNoticeToHaveBeenGiven() {
        UUID community = insertCommunity(1);
        UUID member = insertMember(community, "Test Person");

        assertSqlState(() -> insertConsent(member, "WRITTEN", "MODERATOR", false), CHECK_VIOLATION);
    }

    @Test
    void anOnBehalfConsentWithTheNoticeGivenIsAccepted() {
        UUID community = insertCommunity(1);
        UUID member = insertMember(community, "Test Person");

        insertConsent(member, "VERBAL", "ADMINISTRATOR", true);

        assertThat(jdbc.queryForObject("select count(*) from community_contact_consent", Integer.class)).isOne();
    }

    @Test
    void migratingFromVersionFiveCopiesTheProjectedCommunitiesAsActiveWithoutResponsible() {
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        migrateTo("5");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        jdbc.update("insert into identity_community (id, number) values (?, 11), (?, 12)", first, second);

        migrateTo("6");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, number, status, responsible_member_id, name from community order by number");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("id", first).containsEntry("number", 11)
                .containsEntry("status", "ACTIVE").containsEntry("responsible_member_id", null)
                .containsEntry("name", null);
        assertThat(rows.get(1)).containsEntry("id", second).containsEntry("number", 12);
    }
}
