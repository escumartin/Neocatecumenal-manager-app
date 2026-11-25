package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.GetCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import org.springframework.transaction.annotation.Transactional;

public class GetCommunityService implements GetCommunity {

    private final CommunityRepository communities;

    public GetCommunityService(CommunityRepository communities) {
        this.communities = communities;
    }

    @Override
    @Transactional(readOnly = true)
    public Community get(Actor actor, CommunityId id) {
        MemberAccessPolicy.requireCanViewCommunityFile(actor, id);
        return communities.findById(id).orElseThrow(CommunityNotFound::new);
    }
}
