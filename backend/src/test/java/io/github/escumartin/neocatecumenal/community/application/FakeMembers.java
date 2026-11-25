package io.github.escumartin.neocatecumenal.community.application;

import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class FakeMembers implements MemberRepository {

    final Map<CommunityId, Long> counts = new HashMap<>();
    final List<CommunityId> queried = new ArrayList<>();

    @Override
    public long countIn(CommunityId communityId) {
        queried.add(communityId);
        return counts.getOrDefault(communityId, 0L);
    }
}
