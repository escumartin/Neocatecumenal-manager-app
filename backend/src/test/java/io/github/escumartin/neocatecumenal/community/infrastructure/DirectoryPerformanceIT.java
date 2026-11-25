package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DirectoryPerformanceIT {

    private static final int MEMBERS = 80;
    private static final int WARMUP_REQUESTS = 20;
    private static final int MEASURED_REQUESTS = 100;
    private static final long P95_BUDGET_MILLIS = 200;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private SessionTokenIssuer tokens;

    private String registered;

    @BeforeEach
    void setUp() {
        CommunityApiFixture fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        UUID home = fixture.community(1);
        for (int index = 0; index < MEMBERS; index++) {
            UUID member = fixture.member(home, "Hermano" + index);
            jdbc.update("update community_member set surnames = ?, mobile = ?, landline = ?, email = ? where id = ?",
                    "Apellido" + index, "6000000" + String.format("%02d", index),
                    "9100000" + String.format("%02d", index), "hermano" + index + "@example.org", member);
            for (String field : List.of("MOBILE", "LANDLINE", "EMAIL")) {
                jdbc.update("insert into community_contact_consent (id, member_id, field, decision, channel, "
                        + "recorded_by_user_id, recorder_role, notice_given, recorded_at) "
                        + "values (?, ?, ?, 'GRANTED', 'VERBAL', ?, 'MODERATOR', true, now())", UUID.randomUUID(),
                        member, field, UUID.randomUUID());
            }
        }
        registered = fixture.bearer(UserRole.REGISTERED, home);
    }

    private long timedListMillis() throws Exception {
        long start = System.nanoTime();
        mockMvc.perform(get("/api/members").header(HttpHeaders.AUTHORIZATION, registered))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(MEMBERS));
        return (System.nanoTime() - start) / 1_000_000;
    }

    @Test
    void theDirectoryOfEightyMembersWithContactsAnswersUnder200MillisAtP95() throws Exception {
        for (int request = 0; request < WARMUP_REQUESTS; request++) {
            timedListMillis();
        }
        List<Long> samples = new ArrayList<>();
        for (int request = 0; request < MEASURED_REQUESTS; request++) {
            samples.add(timedListMillis());
        }
        Collections.sort(samples);

        assertThat(samples.get((int) Math.ceil(MEASURED_REQUESTS * 0.95) - 1)).isLessThan(P95_BUDGET_MILLIS);
    }
}
