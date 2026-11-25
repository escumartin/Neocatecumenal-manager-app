package io.github.escumartin.neocatecumenal.community.infrastructure.out.persistence;

import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MemberRepositoryAdapter implements MemberRepository {

    private final JdbcTemplate jdbc;

    MemberRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public long countIn(CommunityId communityId) {
        Long count = jdbc.queryForObject("select count(*) from community_member where community_id = ?",
                Long.class, communityId.value());
        return count == null ? 0 : count;
    }
}
