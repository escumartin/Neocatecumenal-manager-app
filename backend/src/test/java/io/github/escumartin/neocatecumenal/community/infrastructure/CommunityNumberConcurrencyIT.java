package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CommunityNumberConcurrencyIT {

    private static final int ROUNDS = 5;
    private static final int NUMBER = 77;

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

    private String administrator;

    @BeforeEach
    void setUp() {
        CommunityApiFixture fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        UUID home = fixture.community(1);
        administrator = fixture.bearer(UserRole.ADMINISTRATOR, home);
    }

    @RepeatedTest(ROUNDS)
    void twoSimultaneousCreationsOfTheSameNumberYieldOneCreatedAndOneNumberTaken() throws Exception {
        jdbc.update("delete from identity_community where number = ?", NUMBER);
        jdbc.update("delete from community where number = ?", NUMBER);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> creation = () -> {
                barrier.await();
                return mockMvc.perform(post("/api/communities").header(HttpHeaders.AUTHORIZATION, administrator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"number\":" + NUMBER + "}")).andReturn();
            };
            List<Future<MvcResult>> futures = new ArrayList<>();
            futures.add(executor.submit(creation));
            futures.add(executor.submit(creation));

            List<Integer> statuses = new ArrayList<>();
            String rejectedBody = null;
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get();
                statuses.add(result.getResponse().getStatus());
                if (result.getResponse().getStatus() == 422) {
                    rejectedBody = result.getResponse().getContentAsString();
                }
            }

            assertThat(statuses).containsExactlyInAnyOrder(201, 422);
            assertThat(JsonPath.<String>read(rejectedBody, "$.errors[0].code")).isEqualTo("COMMUNITY_NUMBER_TAKEN");
            assertThat(JsonPath.<String>read(rejectedBody, "$.errors[0].field")).isEqualTo("number");
            assertThat(jdbc.queryForObject("select count(*) from community where number = ?", Integer.class,
                    NUMBER)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from identity_community where number = ?",
                    Integer.class, NUMBER)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
