package io.github.escumartin.neocatecumenal.community.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.community.application.port.out.ErasureRecords;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MemberErasureRecordsAdapter implements ErasureRecords {

    private final JdbcTemplate jdbc;

    MemberErasureRecordsAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMember(MemberId memberId, Instant erasedAt) {
        insert(memberId, null, erasedAt);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordContactField(MemberId memberId, ContactField field, Instant erasedAt) {
        insert(memberId, field.name(), erasedAt);
    }

    @Override
    @Transactional
    public int purgeBefore(Instant cutoff) {
        return jdbc.update("delete from community_erasure where erased_at < ?", Timestamp.from(cutoff));
    }

    private void insert(MemberId memberId, String field, Instant erasedAt) {
        jdbc.update("insert into community_erasure (id, member_id, field, erased_at) values (?, ?, ?, ?)",
                UUID.randomUUID(), memberId.value(), field, Timestamp.from(erasedAt));
    }
}
