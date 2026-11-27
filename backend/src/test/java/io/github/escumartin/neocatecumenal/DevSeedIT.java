package io.github.escumartin.neocatecumenal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class DevSeedIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static Map<String, Object> properties(String schema) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server.port", "0");
        properties.put("DATABASE_URL", POSTGRES.getJdbcUrl() + "&currentSchema=" + schema);
        properties.put("DATABASE_USER", POSTGRES.getUsername());
        properties.put("DATABASE_PASSWORD", POSTGRES.getPassword());
        properties.put("spring.datasource.url", POSTGRES.getJdbcUrl() + "&currentSchema=" + schema);
        properties.put("spring.datasource.username", POSTGRES.getUsername());
        properties.put("spring.datasource.password", POSTGRES.getPassword());
        properties.put("spring.flyway.create-schemas", "true");
        properties.put("spring.flyway.default-schema", schema);
        return properties;
    }

    private static int memberCount(String schema, String... profiles) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(NeocatecumenalApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles(profiles)
                .properties(properties(schema))
                .run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            Integer count = jdbc.queryForObject("""
                    select count(*) from community_member m
                    join community c on c.id = m.community_id
                    where c.number = 5 and c.name = 'Quinta comunidad'
                    """, Integer.class);
            return count == null ? 0 : count;
        }
    }

    @Test
    void localProfileLoadsThirtyThreeMembersIntoTheFifthCommunity() {
        assertThat(memberCount("seed_local", "local")).isEqualTo(33);
    }

    @Test
    void localProfileKeepsTheSeedIdempotentAcrossRestarts() {
        memberCount("seed_restart", "local");
        assertThat(memberCount("seed_restart", "local")).isEqualTo(33);
    }

    @Test
    void withoutLocalProfileNoSeedIsLoaded() {
        assertThat(memberCount("seed_none")).isZero();
    }
}
