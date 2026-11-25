package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.OnBehalfConsent;
import java.util.Optional;

public interface ChangeMemberContact {

    MemberView change(Actor actor, MemberId id, ContactChange change, Optional<OnBehalfConsent> consent);
}
