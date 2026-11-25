package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.MemberNotFound;
import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;

final class MemberLocks {

    private MemberLocks() {
    }

    static Member visibleToManager(Actor actor, MemberRepository members, MemberId id) {
        MemberAccessPolicy.requireCanManageMembers(actor);
        Member member = members.findById(id).orElseThrow(MemberNotFound::new);
        if (!MemberAccessPolicy.canManageMembers(actor, member.communityId())) {
            throw new MemberNotFound();
        }
        return member;
    }

    static Member lockedForManagement(Actor actor, CommunityRepository communities, MemberRepository members,
            MemberId id) {
        Member read = visibleToManager(actor, members, id);
        CommunityLocks.lock(communities, read.communityId());
        Member locked = members.lock(id).orElseThrow(MemberNotFound::new);
        if (!locked.communityId().equals(read.communityId())) {
            throw new StaleLockOrderException();
        }
        return locked;
    }

    static boolean isResponsible(CommunityRepository communities, Member member) {
        return communities.findById(member.communityId())
                .map(community -> community.responsibleMemberId().filter(member.id()::equals).isPresent())
                .orElse(false);
    }
}
