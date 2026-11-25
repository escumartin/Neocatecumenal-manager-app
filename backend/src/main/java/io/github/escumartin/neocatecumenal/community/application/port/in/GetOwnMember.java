package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.Actor;

public interface GetOwnMember {

    OwnMemberView get(Actor actor);
}
