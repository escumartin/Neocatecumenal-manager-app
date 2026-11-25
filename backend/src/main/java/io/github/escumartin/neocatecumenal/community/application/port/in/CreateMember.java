package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;
import io.github.escumartin.neocatecumenal.community.domain.OnBehalfConsent;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import java.util.Optional;

public interface CreateMember {

    MemberView create(Actor actor, CommunityId communityId, PersonName name, Optional<ContactChange> contact,
            Optional<OnBehalfConsent> consent);
}
