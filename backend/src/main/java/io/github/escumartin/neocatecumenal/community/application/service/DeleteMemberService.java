package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.DeleteMember;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.ErasureRecords;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class DeleteMemberService implements DeleteMember {

    private static final Logger log = LoggerFactory.getLogger(DeleteMemberService.class);

    private final CommunityRepository communities;
    private final MemberRepository members;
    private final UserDirectory users;
    private final ErasureRecords erasures;
    private final Clock clock;

    public DeleteMemberService(CommunityRepository communities, MemberRepository members, UserDirectory users,
            ErasureRecords erasures, Clock clock) {
        this.communities = communities;
        this.members = members;
        this.users = users;
        this.erasures = erasures;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void delete(Actor actor, MemberId id) {
        Member member = MemberLocks.lockedForManagement(actor, communities, members, id);
        Optional<UserId> linkedUserId = member.linkedUserId();
        Optional<LinkedUser> linkedUser = linkedUserId.flatMap(users::lockUser);
        MemberAccessPolicy.requireCanDelete(actor, member.communityId(), linkedUser);
        Community community = communities.findById(member.communityId()).orElseThrow(CommunityNotFound::new);
        member.checkDeletable(community);
        linkedUser.ifPresent(user -> users.deleteUser(user.id()));
        members.delete(id);
        erasures.recordMember(id, clock.instant());
        log.atInfo().addKeyValue("actorId", actor.id().value())
                .addKeyValue("userDeleted", linkedUser.isPresent()).log("Hermano borrado");
    }
}
