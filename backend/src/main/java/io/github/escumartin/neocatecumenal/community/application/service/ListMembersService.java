package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.ListMembers;
import io.github.escumartin.neocatecumenal.community.application.port.in.MemberView;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;
import io.github.escumartin.neocatecumenal.community.domain.DirectoryOrder;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class ListMembersService implements ListMembers {

    private final CommunityRepository communities;
    private final MemberRepository members;

    public ListMembersService(CommunityRepository communities, MemberRepository members) {
        this.communities = communities;
        this.members = members;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberView> list(Actor actor, CommunityId communityId) {
        MemberAccessPolicy.requireCanManageMembers(actor, communityId);
        Community community = communities.findById(communityId).orElseThrow(CommunityNotFound::new);
        return DirectoryOrder.sorted(members.findByCommunity(communityId)).stream()
                .map(member -> new MemberView(member, community.responsibleMemberId().filter(member.id()::equals)
                        .isPresent()))
                .toList();
    }
}
