package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SchemaMigrationIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private Connection connection;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void openConnectionAndClean() throws SQLException {
        connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        execute("delete from identity_session");
        execute("delete from identity_user");
        execute("delete from identity_community");
        execute("delete from identity_login_pair_throttle");
        execute("delete from identity_login_ip_throttle");
    }

    private void execute(String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private UUID insertCommunity(int number) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into identity_community (id, number) values (?, ?)")) {
            statement.setObject(1, id);
            statement.setInt(2, number);
            statement.executeUpdate();
        }
        return id;
    }

    private UUID insertUser(UUID communityId, int accessId, String role, String noticeVersion, boolean accepted)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into identity_user (id, access_id, name, role, community_id, password_hash, "
                        + "password_change_required, privacy_notice_version, privacy_notice_accepted_at, version) "
                        + "values (?, ?, 'Test User', ?, ?, '{argon2}x', true, ?, "
                        + (accepted ? "now()" : "null") + ", 0)")) {
            statement.setObject(1, id);
            statement.setInt(2, accessId);
            statement.setString(3, role);
            statement.setObject(4, communityId);
            statement.setString(5, noticeVersion);
            statement.executeUpdate();
        }
        return id;
    }

    private UUID insertSession(UUID userId, byte[] tokenHash) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into identity_session (id, user_id, token_hash, created_at, last_used_at) "
                        + "values (?, ?, ?, now(), now())")) {
            statement.setObject(1, id);
            statement.setObject(2, userId);
            statement.setBytes(3, tokenHash);
            statement.executeUpdate();
        }
        return id;
    }

    private int count(String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select count(*) from " + table);
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    @Test
    void insertsAValidUser() throws SQLException {
        UUID community = insertCommunity(1);

        insertUser(community, 100000, "REGISTERED", null, false);

        assertThat(count("identity_user")).isEqualTo(1);
    }

    @Test
    void rejectsDuplicatedAccessId() throws SQLException {
        UUID community = insertCommunity(1);
        insertUser(community, 123456, "REGISTERED", null, false);

        assertThatThrownBy(() -> insertUser(community, 123456, "MODERATOR", null, false))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("uk_identity_user_access_id");
    }

    @Test
    void rejectsAccessIdBelowRange() throws SQLException {
        UUID community = insertCommunity(1);

        assertThatThrownBy(() -> insertUser(community, 99999, "REGISTERED", null, false))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23514");
    }

    @Test
    void rejectsAccessIdAboveRange() throws SQLException {
        UUID community = insertCommunity(1);

        assertThatThrownBy(() -> insertUser(community, 1000000, "REGISTERED", null, false))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23514");
    }

    @Test
    void rejectsUnknownRole() throws SQLException {
        UUID community = insertCommunity(1);

        assertThatThrownBy(() -> insertUser(community, 123456, "OWNER", null, false))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23514");
    }

    @Test
    void acceptsAdministratorAndModeratorRoles() throws SQLException {
        UUID community = insertCommunity(1);

        insertUser(community, 123456, "ADMINISTRATOR", null, false);
        insertUser(community, 123457, "MODERATOR", null, false);

        assertThat(count("identity_user")).isEqualTo(2);
    }

    @Test
    void rejectsNoticeVersionWithoutAcceptanceDate() throws SQLException {
        UUID community = insertCommunity(1);

        assertThatThrownBy(() -> insertUser(community, 123456, "REGISTERED", "v1", false))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23514");
    }

    @Test
    void rejectsAcceptanceDateWithoutNoticeVersion() throws SQLException {
        UUID community = insertCommunity(1);

        assertThatThrownBy(() -> insertUser(community, 123456, "REGISTERED", null, true))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23514");
    }

    @Test
    void acceptsNoticeVersionWithAcceptanceDate() throws SQLException {
        UUID community = insertCommunity(1);

        insertUser(community, 123456, "REGISTERED", "v1", true);

        assertThat(count("identity_user")).isEqualTo(1);
    }

    @Test
    void rejectsDuplicatedCommunityNumber() throws SQLException {
        insertCommunity(7);

        assertThatThrownBy(() -> insertCommunity(7))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23505");
    }

    @Test
    void rejectsNonPositiveCommunityNumber() {
        assertThatThrownBy(() -> insertCommunity(0))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23514");
    }

    @Test
    void rejectsUserWithUnknownCommunity() {
        assertThatThrownBy(() -> insertUser(UUID.randomUUID(), 123456, "REGISTERED", null, false))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23503");
    }

    @Test
    void deletingAUserDeletesItsSessions() throws SQLException {
        UUID community = insertCommunity(1);
        UUID user = insertUser(community, 123456, "REGISTERED", null, false);
        insertSession(user, new byte[] {1, 2, 3});
        insertSession(user, new byte[] {4, 5, 6});

        execute("delete from identity_user");

        assertThat(count("identity_session")).isZero();
    }

    @Test
    void rejectsDuplicatedSessionTokenHash() throws SQLException {
        UUID community = insertCommunity(1);
        UUID user = insertUser(community, 123456, "REGISTERED", null, false);
        insertSession(user, new byte[] {1, 2, 3});

        assertThatThrownBy(() -> insertSession(user, new byte[] {1, 2, 3}))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23505");
    }

    @Test
    void throttleTablesAcceptRowsWithoutUserReference() throws SQLException {
        execute("insert into identity_login_pair_throttle (access_id, ip, consecutive_failures, last_failure_at) "
                + "values (999999, '10.0.0.1', 1, now())");
        execute("insert into identity_login_ip_throttle (ip, recent_failures) "
                + "values ('10.0.0.1', array[now()]::timestamptz[])");

        assertThat(count("identity_login_pair_throttle")).isEqualTo(1);
        assertThat(count("identity_login_ip_throttle")).isEqualTo(1);
    }

    @Test
    void ipThrottleRejectsMoreThanTwentyInstants() {
        assertThatThrownBy(() -> execute("insert into identity_login_ip_throttle (ip, recent_failures) "
                + "values ('10.0.0.2', array_fill(now(), array[21])::timestamptz[])"))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23514");
    }
}
