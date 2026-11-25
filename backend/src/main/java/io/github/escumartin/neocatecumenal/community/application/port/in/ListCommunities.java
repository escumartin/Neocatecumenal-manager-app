package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import java.util.List;

public interface ListCommunities {

    List<Community> list(Actor actor, boolean includeDeactivated);
}
