package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.MemberView;
import io.github.escumartin.neocatecumenal.community.application.port.in.TransferMember;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.MemberNotFound;
import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class TransferMemberService implements TransferMember {

    private static final Logger log = LoggerFactory.getLogger(TransferMemberService.class);

    private final CommunityRepository communities;
    private final MemberRepository members;
    private final UserDirectory users;

    public TransferMemberService(CommunityRepository communities, MemberRepository members, UserDirectory users) {
        this.communities = communities;
        this.members = members;
        this.users = users;
    }

    @Override
    @Transactional
    public MemberView transfer(Actor actor, MemberId id, CommunityId targetId) {
        MemberAccessPolicy.requireCanTransfer(actor);
        CommunityId originId = members.findById(id).orElseThrow(MemberNotFound::new).communityId();
        List<Community> locked = communities.lockInIdOrder(originId, targetId);
        Community origin = find(locked, originId);
        Community target = find(locked, targetId);
        Member member = members.lock(id).orElseThrow(MemberNotFound::new);
        if (!member.communityId().equals(originId)) {
            throw new StaleLockOrderException();
        }
        member.transferTo(origin, target);
        if (!originId.equals(targetId)) {
            members.save(member);
            member.linkedUserId().ifPresent(userId -> users.moveUser(userId, targetId));
            log.atInfo().addKeyValue("actorId", actor.id().value()).addKeyValue("memberId", id.value())
                    .addKeyValue("fromCommunityId", originId.value()).addKeyValue("toCommunityId", targetId.value())
                    .log("Hermano trasladado de comunidad");
        }
        return new MemberView(member, MemberLocks.isResponsible(communities, member));
    }

    private static Community find(List<Community> locked, CommunityId id) {
        return locked.stream().filter(community -> community.id().equals(id)).findFirst()
                .orElseThrow(CommunityNotFound::new);
    }
}
