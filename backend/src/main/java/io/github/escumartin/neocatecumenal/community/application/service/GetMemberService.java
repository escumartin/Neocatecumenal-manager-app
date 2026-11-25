package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.GetMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.MemberView;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import org.springframework.transaction.annotation.Transactional;

public class GetMemberService implements GetMember {

    private final CommunityRepository communities;
    private final MemberRepository members;

    public GetMemberService(CommunityRepository communities, MemberRepository members) {
        this.communities = communities;
        this.members = members;
    }

    @Override
    @Transactional(readOnly = true)
    public MemberView get(Actor actor, MemberId id) {
        Member member = MemberLocks.visibleToManager(actor, members, id);
        return new MemberView(member, MemberLocks.isResponsible(communities, member));
    }
}
