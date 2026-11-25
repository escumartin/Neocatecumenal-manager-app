package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;

public interface ChangeOwnContact {

    OwnMemberView change(Actor actor, ContactChange change);
}
