package io.github.escumartin.neocatecumenal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private static ConfigurableApplicationContext start(String schema, String... profiles) {
        return new SpringApplicationBuilder(NeocatecumenalApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles(profiles)
                .properties(properties(schema))
                .run();
    }

    private static int memberCount(String schema, String... profiles) {
        try (ConfigurableApplicationContext context = start(schema, profiles)) {
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

    @Test
    void localProfileCreatesAModeratorLinkedToAMemberOfTheFifthCommunity() {
        try (ConfigurableApplicationContext context = start("seed_moderator", "local")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            Map<String, Object> row = jdbc.queryForMap("""
                    select u.role, u.access_id, u.password_change_required, c.number, m.given_name
                    from identity_user u
                    join identity_community c on c.id = u.community_id
                    join community_member m on m.user_id = u.id
                    where u.access_id = 100005
                    """);
            assertThat(row).containsEntry("role", "MODERATOR")
                    .containsEntry("number", 5)
                    .containsEntry("password_change_required", false)
                    .containsEntry("given_name", "Antonio");
        }
    }

    @Test
    void moderatorSeedStaysSingleAcrossRestarts() {
        try (ConfigurableApplicationContext first = start("seed_moderator_restart", "local")) {
            assertThat(first).isNotNull();
        }
        try (ConfigurableApplicationContext second = start("seed_moderator_restart", "local")) {
            Integer count = second.getBean(JdbcTemplate.class)
                    .queryForObject("select count(*) from identity_user where role = 'MODERATOR'", Integer.class);
            assertThat(count).isEqualTo(1);
        }
    }

    @Test
    void withoutLocalProfileNoModeratorIsLoaded() {
        try (ConfigurableApplicationContext context = start("seed_moderator_none")) {
            Integer count = context.getBean(JdbcTemplate.class)
                    .queryForObject("select count(*) from identity_user", Integer.class);
            assertThat(count).isZero();
        }
    }

    @Test
    void moderatorCanLogInWithTheSeededCredentials() throws Exception {
        String password = System.getenv("DEV_SEED_MODERATOR_PASSWORD");
        assumeTrue(password != null && !password.isBlank());
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(NeocatecumenalApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("local")
                .properties(properties("seed_login"))
                .run()) {
            String port = context.getEnvironment().getProperty("local.server.port");
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/sessions"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "{\"accessId\":100005,\"password\":\"" + password + "\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.body()).contains("MODERATOR");
        }
    }
}
