package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import java.util.List;

public interface ListMembers {

    List<MemberView> list(Actor actor, CommunityId communityId);
}
