package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.MemberView;
import io.github.escumartin.neocatecumenal.community.application.port.in.RenameMember;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class RenameMemberService implements RenameMember {

    private static final Logger log = LoggerFactory.getLogger(RenameMemberService.class);

    private final CommunityRepository communities;
    private final MemberRepository members;

    public RenameMemberService(CommunityRepository communities, MemberRepository members) {
        this.communities = communities;
        this.members = members;
    }

    @Override
    @Transactional
    public MemberView rename(Actor actor, MemberId id, PersonName name) {
        Member member = MemberLocks.lockedForManagement(actor, communities, members, id);
        member.rename(name);
        members.save(member);
        log.atInfo().addKeyValue("actorId", actor.id().value()).addKeyValue("memberId", id.value())
                .log("Hermano renombrado");
        return new MemberView(member, MemberLocks.isResponsible(communities, member));
    }
}
