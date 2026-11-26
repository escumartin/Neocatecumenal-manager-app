package io.github.escumartin.neocatecumenal.spaces.application.port.out;

import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;

public interface CommunityLock {

    boolean lockForShare(CommunityId id);
}
