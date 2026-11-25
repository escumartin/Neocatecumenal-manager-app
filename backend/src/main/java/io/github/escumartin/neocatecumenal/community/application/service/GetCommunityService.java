package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.CommunityView;
import io.github.escumartin.neocatecumenal.community.application.port.in.GetCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import org.springframework.transaction.annotation.Transactional;

public class GetCommunityService implements GetCommunity {

    private final CommunityRepository communities;

    private final MemberRepository members;
    private final UserDirectory users;

    public GetCommunityService(CommunityRepository communities, MemberRepository members, UserDirectory users) {
        this.communities = communities;
        this.members = members;
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public CommunityView get(Actor actor, CommunityId id) {
        MemberAccessPolicy.requireCanViewCommunityFile(actor, id);
        return CommunityViews.of(communities.findById(id).orElseThrow(CommunityNotFound::new), members, users);
    }
}
