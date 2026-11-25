package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.UnlinkMember;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class UnlinkMemberService implements UnlinkMember {

    private static final Logger log = LoggerFactory.getLogger(UnlinkMemberService.class);

    private final CommunityRepository communities;
    private final MemberRepository members;
    private final UserDirectory users;

    public UnlinkMemberService(CommunityRepository communities, MemberRepository members, UserDirectory users) {
        this.communities = communities;
        this.members = members;
        this.users = users;
    }

    @Override
    @Transactional
    public void unlink(Actor actor, MemberId id) {
        Member member = MemberLocks.lockedForManagement(actor, communities, members, id);
        member.linkedUserId().ifPresent(userId -> MemberAccessPolicy.requireCanUnlink(actor,
                member.communityId(), users.user(userId)));
        member.unlink();
        members.save(member);
        log.atInfo().addKeyValue("actorId", actor.id().value()).addKeyValue("memberId", id.value())
                .log("Hermano desvinculado de su usuario");
    }
}
