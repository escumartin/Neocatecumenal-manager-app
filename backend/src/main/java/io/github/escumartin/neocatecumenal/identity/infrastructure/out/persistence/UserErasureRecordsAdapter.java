package io.github.escumartin.neocatecumenal.identity.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.identity.application.port.out.ErasureRecords;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserErasureRecordsAdapter implements ErasureRecords {

    private final JdbcTemplate jdbc;

    UserErasureRecordsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUser(UserId userId, Instant erasedAt) {
        jdbc.update("insert into identity_erasure (id, user_id, erased_at) values (?, ?, ?)",
                UUID.randomUUID(), userId.value(), Timestamp.from(erasedAt));
    }

    @Override
    @Transactional
    public int purgeBefore(Instant cutoff) {
        return jdbc.update("delete from identity_erasure where erased_at < ?", Timestamp.from(cutoff));
    }
}
