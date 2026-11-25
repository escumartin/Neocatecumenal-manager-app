package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;

public interface RenameMember {

    MemberView rename(Actor actor, MemberId id, PersonName name);
}
