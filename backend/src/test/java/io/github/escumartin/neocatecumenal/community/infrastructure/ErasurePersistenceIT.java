package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.service.PurgeExpiredErasuresService;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.infrastructure.out.persistence.MemberErasureRecordsAdapter;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MemberErasureRecordsAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ErasurePersistenceIT {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private MemberErasureRecordsAdapter erasures;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTable() {
        jdbc.update("delete from community_erasure");
    }

    private List<UUID> storedMemberIds() {
        return jdbc.queryForList("select member_id from community_erasure", UUID.class);
    }

    private List<String> storedRows() {
        return jdbc.query("select member_id, field from community_erasure",
                (rs, row) -> rs.getObject("member_id", UUID.class) + ":" + rs.getString("field"));
    }

    @Test
    void aMemberErasureIsWrittenWithNullFieldAndTheInstant() {
        MemberId member = new MemberId(UUID.randomUUID());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> erasures.recordMember(member, NOW));

        assertThat(storedRows()).containsExactly(member.value() + ":null");
        assertThat(jdbc.queryForObject("select erased_at from community_erasure", Timestamp.class).toInstant())
                .isEqualTo(NOW);
    }

    @Test
    void aContactFieldErasureIsWrittenWithTheFieldName() {
        MemberId member = new MemberId(UUID.randomUUID());

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> erasures.recordContactField(member, ContactField.LANDLINE, NOW));

        assertThat(storedRows()).containsExactly(member.value() + ":LANDLINE");
    }

    @Test
    void theRowsAreRolledBackWithTheTransactionOfTheDeletion() {
        MemberId member = new MemberId(UUID.randomUUID());

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            erasures.recordMember(member, NOW);
            erasures.recordContactField(member, ContactField.EMAIL, NOW);
            throw new IllegalStateException("deletion failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(storedRows()).isEmpty();
    }

    @Test
    void recordingOutsideATransactionIsRefused() {
        assertThatThrownBy(() -> erasures.recordMember(new MemberId(UUID.randomUUID()), NOW))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> erasures.recordContactField(new MemberId(UUID.randomUUID()), ContactField.MOBILE,
                NOW)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void purgingRemovesOnlyRowsOlderThanTheCutoff() {
        MemberId old = new MemberId(UUID.randomUUID());
        MemberId recent = new MemberId(UUID.randomUUID());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            erasures.recordMember(old, NOW.minus(Duration.ofDays(36)));
            erasures.recordContactField(recent, ContactField.MOBILE, NOW.minus(Duration.ofDays(34)));
        });

        int purged = erasures.purgeBefore(NOW.minus(Duration.ofDays(35)));

        assertThat(purged).isEqualTo(1);
        assertThat(storedRows()).containsExactly(recent.value() + ":MOBILE");
    }

    @Test
    void theUseCaseOnlyTouchesTheErasureTable() {
        MemberId old = new MemberId(UUID.randomUUID());
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> erasures.recordMember(old, NOW.minus(Duration.ofDays(40))));
        int membersBefore = jdbc.queryForObject("select count(*) from community_member", Integer.class);

        new PurgeExpiredErasuresService(erasures, Clock.fixed(NOW, ZoneOffset.UTC)).purge();

        assertThat(storedRows()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from community_member", Integer.class))
                .isEqualTo(membersBefore);
    }
}
