package io.github.escumartin.neocatecumenal.community.infrastructure;

import static io.github.escumartin.neocatecumenal.FlywayTestSupport.assertSqlState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
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
class CommunityErasureMigrationIT {

    private static final String NOT_NULL_VIOLATION = "23502";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchema() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
    }

    private void migrateTo(String target) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load()
                .migrate();
    }

    private List<Map<String, Object>> columns() {
        return jdbc.queryForList("select column_name, data_type, is_nullable, character_maximum_length "
                + "from information_schema.columns where table_name = 'community_erasure' order by ordinal_position");
    }

    @Test
    void columnsHaveTheDocumentedTypesAndNullability() {
        migrateTo("8");

        assertThat(columns()).extracting(column -> column.get("column_name"), column -> column.get("data_type"),
                column -> column.get("is_nullable"), column -> column.get("character_maximum_length"))
                .containsExactly(tuple("id", "uuid", "NO", null), tuple("member_id", "uuid", "NO", null), tuple("field", "character varying", "YES", 16), tuple("erased_at", "timestamp with time zone", "NO", null));
    }

    @Test
    void erasedAtIsIndexed() {
        migrateTo("8");

        assertThat(jdbc.queryForList("select indexdef from pg_indexes where tablename = 'community_erasure'", String.class))
                .anyMatch(definition -> definition.contains("(erased_at)"));
    }

    @Test
    void theTableHasNoForeignKeys() {
        migrateTo("8");

        assertThat(jdbc.queryForList("select constraint_name from information_schema.table_constraints "
                + "where table_name = 'community_erasure' and constraint_type = 'FOREIGN KEY'", String.class)).isEmpty();
    }

    @Test
    void erasedAtIsRequired() {
        migrateTo("8");

        assertSqlState(() -> jdbc.update("insert into community_erasure (id, member_id) values (?, ?)", UUID.randomUUID(), UUID.randomUUID()), NOT_NULL_VIOLATION);
    }

    @Test
    void theMigrationOnlyAddsAndKeepsExistingTables() {
        migrateTo("7");
        List<String> before = jdbc.queryForList("select table_name || '.' || column_name from information_schema.columns "
                + "where table_schema = 'public' and table_name <> 'flyway_schema_history' order by 1", String.class);

        migrateTo("8");

        assertThat(jdbc.queryForList("select table_name || '.' || column_name from information_schema.columns "
                + "where table_schema = 'public' and table_name <> 'community_erasure' "
                + "and table_name <> 'flyway_schema_history' order by 1", String.class)).isEqualTo(before);
    }
}
