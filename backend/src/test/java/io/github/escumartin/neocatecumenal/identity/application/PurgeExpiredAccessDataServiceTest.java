package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemorySessions;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryThrottleStore;
import io.github.escumartin.neocatecumenal.identity.application.service.PurgeExpiredAccessDataService;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle.IpState;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle.PairState;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurgeExpiredAccessDataServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Test
    void purgesExpiredSessionsAndThrottleRowsAndKeepsTheLiveOnes() {
        InMemorySessions sessions = new InMemorySessions();
        InMemoryThrottleStore throttle = new InMemoryThrottleStore();
        UserId user = new UserId(UUID.randomUUID());
        sessions.save(new Session(UUID.randomUUID(), user, new byte[] {1}, NOW.minus(Duration.ofDays(40)),
                NOW.minus(Duration.ofDays(31))));
        sessions.save(new Session(UUID.randomUUID(), user, new byte[] {2}, NOW, NOW));
        throttle.save("10.0.0.1", new IpState(List.of(NOW.minus(Duration.ofHours(1))), null));
        throttle.save("10.0.0.2", new IpState(List.of(NOW.minusSeconds(30)), null));
        throttle.save(new AccessId(123456), "10.0.0.1", new PairState(2, NOW.minus(Duration.ofHours(1)), null));

        new PurgeExpiredAccessDataService(sessions, throttle, Clock.fixed(NOW, ZoneOffset.UTC)).purge();

        assertThat(sessions.sessions).hasSize(1);
        assertThat(throttle.ips).containsOnlyKeys("10.0.0.2");
        assertThat(throttle.pairs).isEmpty();
    }
}
