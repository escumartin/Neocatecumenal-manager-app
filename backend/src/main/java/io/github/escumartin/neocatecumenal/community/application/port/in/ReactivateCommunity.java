package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;

public interface ReactivateCommunity {

    void reactivate(Actor actor, CommunityId id);
}
