package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.postgresql.PostgreSQLContainer;

class UserDeletionVsMemberDeletionRaceIT extends AbstractRaceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final int ROUNDS = 10;

    private Callable<RaceHarness.Response> deleteMember(String bearer, UUID member) {
        return () -> harness.send(delete("/api/members/" + member).header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private int memberRows(UUID member) {
        return jdbc.queryForObject("select count(*) from community_member where id = ?", Integer.class, member);
    }

    @Test
    void deletingTheUserAndDeletingItsMemberNeverDeadlockNorLeaveAPartialState() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            UUID member = fixture.member(communityA, "Ana " + round);
            UUID user = fixture.user(UserRole.REGISTERED, communityA);
            linkInDatabase(member, user);

            List<RaceHarness.Response> responses = harness.simultaneously(List.of(
                    deleteUser(administratorSession(), user), deleteMember(administratorSession(), member)));

            assertThat(responses).extracting(RaceHarness.Response::status).allMatch(
                    status -> status == 204 || status == 404 || status == 409);
            assertThat(responses).extracting(RaceHarness.Response::status).contains(204);
            assertThat(userExists(user)).isFalse();
            assertThat(danglingLinks()).isZero();
            if (memberRows(member) == 1) {
                assertThat(linkedUserOf(member)).isNull();
            }
        }
        harness.assertNoDeadlocksSinceStart();
    }
}
