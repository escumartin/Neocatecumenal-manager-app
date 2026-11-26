package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.application.service.PurgeExpiredErasuresService;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence.UserErasureRecordsAdapter;
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
@Import(UserErasureRecordsAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ErasurePersistenceIT {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private UserErasureRecordsAdapter erasures;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTable() {
        jdbc.update("delete from identity_erasure");
    }

    private List<UUID> storedUserIds() {
        return jdbc.queryForList("select user_id from identity_erasure", UUID.class);
    }

    @Test
    void theRowIsWrittenWithTheTransactionOfTheDeletion() {
        UserId user = new UserId(UUID.randomUUID());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> erasures.recordUser(user, NOW));

        assertThat(storedUserIds()).containsExactly(user.value());
        assertThat(jdbc.queryForObject("select erased_at from identity_erasure", Timestamp.class).toInstant())
                .isEqualTo(NOW);
    }

    @Test
    void theRowIsRolledBackWithTheTransactionOfTheDeletion() {
        UserId user = new UserId(UUID.randomUUID());

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            erasures.recordUser(user, NOW);
            throw new IllegalStateException("deletion failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(storedUserIds()).isEmpty();
    }

    @Test
    void recordingOutsideATransactionIsRefused() {
        assertThatThrownBy(() -> erasures.recordUser(new UserId(UUID.randomUUID()), NOW))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void purgingRemovesOnlyRowsOlderThanTheCutoff() {
        UserId old = new UserId(UUID.randomUUID());
        UserId recent = new UserId(UUID.randomUUID());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            erasures.recordUser(old, NOW.minus(Duration.ofDays(36)));
            erasures.recordUser(recent, NOW.minus(Duration.ofDays(34)));
        });

        int purged = erasures.purgeBefore(NOW.minus(Duration.ofDays(35)));

        assertThat(purged).isEqualTo(1);
        assertThat(storedUserIds()).containsExactly(recent.value());
    }

    @Test
    void theUseCaseOnlyTouchesTheErasureTable() {
        UserId old = new UserId(UUID.randomUUID());
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> erasures.recordUser(old, NOW.minus(Duration.ofDays(40))));
        int usersBefore = jdbc.queryForObject("select count(*) from identity_user", Integer.class);

        new PurgeExpiredErasuresService(erasures, Clock.fixed(NOW, ZoneOffset.UTC)).purge();

        assertThat(storedUserIds()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from identity_user", Integer.class)).isEqualTo(usersBefore);
    }
}
