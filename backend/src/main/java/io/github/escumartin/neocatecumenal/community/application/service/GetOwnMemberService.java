package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.GetOwnMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.OwnMemberView;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import org.springframework.transaction.annotation.Transactional;

public class GetOwnMemberService implements GetOwnMember {

    private final CommunityRepository communities;
    private final MemberRepository members;
    private final UserDirectory users;

    public GetOwnMemberService(CommunityRepository communities, MemberRepository members, UserDirectory users) {
        this.communities = communities;
        this.members = members;
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public OwnMemberView get(Actor actor) {
        return OwnMemberViews.of(
                members.findByLinkedUser(actor.id())
                        .orElseThrow(() -> new CommunityDomainException("MEMBER_NOT_LINKED")),
                communities, users);
    }
}
