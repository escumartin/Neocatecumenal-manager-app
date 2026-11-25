package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;

final class CommunityLocks {

    private CommunityLocks() {
    }

    static Community lock(CommunityRepository communities, CommunityId id) {
        return communities.lockInIdOrder(id).stream().findFirst().orElseThrow(CommunityNotFound::new);
    }
}
