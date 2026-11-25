package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.LinkMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.MemberView;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.CommunityFieldInvalid;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class LinkMemberService implements LinkMember {

    private static final Logger log = LoggerFactory.getLogger(LinkMemberService.class);

    private final CommunityRepository communities;
    private final MemberRepository members;
    private final UserDirectory users;

    public LinkMemberService(CommunityRepository communities, MemberRepository members, UserDirectory users) {
        this.communities = communities;
        this.members = members;
        this.users = users;
    }

    @Override
    @Transactional
    public MemberView link(Actor actor, MemberId id, UserId userId) {
        Member member = MemberLocks.lockedForManagement(actor, communities, members, id);
        LinkedUser user = users.lockUser(userId)
                .orElseThrow(() -> new CommunityFieldInvalid("userId", "USER_NOT_FOUND"));
        MemberAccessPolicy.requireCanLink(actor, member.communityId(), user);
        member.link(user);
        members.save(member);
        log.atInfo().addKeyValue("actorId", actor.id().value()).addKeyValue("memberId", id.value())
                .addKeyValue("userId", userId.value()).log("Hermano vinculado a un usuario");
        return new MemberView(member, MemberLocks.isResponsible(communities, member));
    }
}
