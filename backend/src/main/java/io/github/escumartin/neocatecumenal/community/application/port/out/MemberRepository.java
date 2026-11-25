package io.github.escumartin.neocatecumenal.community.application.port.out;

import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import java.util.List;
import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(MemberId id);

    Optional<Member> lock(MemberId id);

    void delete(MemberId id);

    List<Member> findByCommunity(CommunityId communityId);

    long countIn(CommunityId communityId);

    Optional<Member> findByLinkedUser(UserId userId);
}
