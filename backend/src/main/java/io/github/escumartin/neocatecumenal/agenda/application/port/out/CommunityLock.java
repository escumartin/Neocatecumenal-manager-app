package io.github.escumartin.neocatecumenal.agenda.application.port.out;

import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;

public interface CommunityLock {

    boolean lockForShare(CommunityId id);
}
