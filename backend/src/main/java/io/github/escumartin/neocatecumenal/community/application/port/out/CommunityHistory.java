package io.github.escumartin.neocatecumenal.community.application.port.out;

import io.github.escumartin.neocatecumenal.community.domain.CommunityId;

public interface CommunityHistory {

    boolean hasHistory(CommunityId communityId);
}
