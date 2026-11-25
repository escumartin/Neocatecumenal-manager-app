package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static io.github.escumartin.neocatecumenal.FlywayTestSupport.assertSqlState;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class CommunityActiveMigrationIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private DataSource dataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @BeforeEach
    void cleanDatabase() {
        Flyway.configure().dataSource(dataSource()).cleanDisabled(false).load().clean();
    }

    private void migrateTo(String target) {
        Flyway.configure().dataSource(dataSource()).locations("classpath:db/migration").target(target).load()
                .migrate();
    }

    @Test
    void existingCommunitiesStayActiveAfterTheMigration() {
        migrateTo("4");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        jdbc.update("insert into identity_community (id, number) values (?, 1), (?, 2)", first, second);

        migrateTo("5");

        assertThat(jdbc.queryForList("select active from identity_community", Boolean.class))
                .containsExactly(true, true);
    }

    @Test
    void activeIsNotNullAndDefaultsToTrue() {
        migrateTo("5");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        UUID id = UUID.randomUUID();

        jdbc.update("insert into identity_community (id, number) values (?, 90)", id);

        assertThat(jdbc.queryForObject("select active from identity_community where id = ?", Boolean.class, id))
                .isTrue();
        assertSqlState(() -> jdbc.update("update identity_community set active = null where id = ?", id), "23502");
    }
}
