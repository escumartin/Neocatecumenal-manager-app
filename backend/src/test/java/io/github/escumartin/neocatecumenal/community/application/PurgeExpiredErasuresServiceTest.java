package io.github.escumartin.neocatecumenal.community.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.community.application.InMemoryErasures.Entry;
import io.github.escumartin.neocatecumenal.community.application.service.PurgeExpiredErasuresService;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
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

    private Entry recordMemberAt(Duration age) {
        MemberId member = new MemberId(UUID.randomUUID());
        erasures.recordMember(member, NOW.minus(age));
        return erasures.entries.getLast();
    }

    private Entry recordFieldAt(Duration age) {
        erasures.recordContactField(new MemberId(UUID.randomUUID()), ContactField.MOBILE, NOW.minus(age));
        return erasures.entries.getLast();
    }

    @Test
    void purgesOnlyRowsOlderThanThirtyFiveDays() {
        recordMemberAt(Duration.ofDays(36));
        recordFieldAt(Duration.ofDays(35).plusSeconds(1));
        Entry atTheLimit = recordMemberAt(Duration.ofDays(35));
        Entry recent = recordFieldAt(Duration.ofDays(34));
        Entry today = recordMemberAt(Duration.ZERO);

        service.purge();

        assertThat(erasures.entries).containsExactly(atTheLimit, recent, today);
    }

    @Test
    void doesNothingWhenThereAreNoExpiredRows() {
        Entry recent = recordMemberAt(Duration.ofDays(1));

        service.purge();

        assertThat(erasures.entries).containsExactly(recent);
    }
}
