package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryErasures;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryErasures.Entry;
import io.github.escumartin.neocatecumenal.identity.application.service.PurgeExpiredErasuresService;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurgeExpiredErasuresServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    private final InMemoryErasures erasures = new InMemoryErasures();
    private final PurgeExpiredErasuresService service =
            new PurgeExpiredErasuresService(erasures, Clock.fixed(NOW, ZoneOffset.UTC));

    private Entry recordAt(Duration age) {
        Entry entry = new Entry(new UserId(UUID.randomUUID()), NOW.minus(age));
        erasures.recordUser(entry.userId(), entry.erasedAt());
        return entry;
    }

    @Test
    void purgesOnlyRowsOlderThanThirtyFiveDays() {
        recordAt(Duration.ofDays(36));
        recordAt(Duration.ofDays(35).plusSeconds(1));
        Entry atTheLimit = recordAt(Duration.ofDays(35));
        Entry recent = recordAt(Duration.ofDays(34));
        Entry today = recordAt(Duration.ZERO);

        service.purge();

        assertThat(erasures.entries).containsExactly(atTheLimit, recent, today);
    }

    @Test
    void doesNothingWhenThereAreNoExpiredRows() {
        Entry recent = recordAt(Duration.ofDays(1));

        service.purge();

        assertThat(erasures.entries).containsExactly(recent);
    }
}
