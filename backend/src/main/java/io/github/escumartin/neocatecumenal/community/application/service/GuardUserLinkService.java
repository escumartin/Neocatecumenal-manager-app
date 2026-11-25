package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.GuardUserLink;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class GuardUserLinkService implements GuardUserLink {

    private static final Logger log = LoggerFactory.getLogger(GuardUserLinkService.class);

    private final CommunityRepository communities;
    private final MemberRepository members;

    public GuardUserLinkService(CommunityRepository communities, MemberRepository members) {
        this.communities = communities;
        this.members = members;
    }

    @Override
    @Transactional
    public boolean allowsCommunityChange(UserId userId, CommunityId current, CommunityId target) {
        List<CommunityId> lockedIds = lock(current, target);
        Optional<Member> linked = members.findByLinkedUser(userId);
        if (linked.isEmpty()) {
            return true;
        }
        Member member = lockedWithinLockedCommunities(linked.get(), lockedIds);
        return member.communityId().equals(target);
    }

    @Override
    @Transactional
    public void releaseBeforeDeletion(UserId userId, CommunityId current) {
        Optional<Member> read = members.findByLinkedUser(userId);
        List<CommunityId> lockedIds = lock(current, read.map(Member::communityId).orElse(current));
        Optional<Member> linked = read.isPresent() ? read : members.findByLinkedUser(userId);
        if (linked.isEmpty()) {
            return;
        }
        Member member = lockedWithinLockedCommunities(linked.get(), lockedIds);
        if (member.linkedUserId().filter(userId::equals).isEmpty()) {
            return;
        }
        member.unlink();
        members.save(member);
        log.atInfo().addKeyValue("memberId", member.id().value()).log("Hermano desvinculado antes de borrar su usuario");
    }

    private List<CommunityId> lock(CommunityId first, CommunityId second) {
        List<CommunityId> ids = Stream.of(first, second).distinct().toList();
        communities.lockInIdOrder(ids.toArray(CommunityId[]::new));
        return ids;
    }

    private Member lockedWithinLockedCommunities(Member read, List<CommunityId> lockedIds) {
        requireWithin(read, lockedIds);
        Member locked = members.lock(read.id()).orElseThrow(StaleLockOrderException::new);
        requireWithin(locked, lockedIds);
        return locked;
    }

    private static void requireWithin(Member member, List<CommunityId> lockedIds) {
        if (!lockedIds.contains(member.communityId())) {
            throw new StaleLockOrderException();
        }
    }
}
