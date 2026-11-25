package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;

public interface GetMember {

    MemberView get(Actor actor, MemberId id);
}
