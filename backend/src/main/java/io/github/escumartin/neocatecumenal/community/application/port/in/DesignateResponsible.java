package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;

public interface DesignateResponsible {

    CommunityView designate(Actor actor, CommunityId communityId, MemberId memberId);
}
