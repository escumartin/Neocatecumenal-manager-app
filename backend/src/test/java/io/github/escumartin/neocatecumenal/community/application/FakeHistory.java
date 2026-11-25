package io.github.escumartin.neocatecumenal.community.application;

import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityHistory;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import java.util.HashSet;
import java.util.Set;

final class FakeHistory implements CommunityHistory {

    final Set<CommunityId> withHistory = new HashSet<>();

    @Override
    public boolean hasHistory(CommunityId communityId) {
        return withHistory.contains(communityId);
    }
}
