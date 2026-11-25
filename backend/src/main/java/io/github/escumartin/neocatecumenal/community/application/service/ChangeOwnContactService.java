package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.ChangeOwnContact;
import io.github.escumartin.neocatecumenal.community.application.port.in.OwnMemberView;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.ConsentContext;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberNotFound;
import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class ChangeOwnContactService implements ChangeOwnContact {

    private static final Logger log = LoggerFactory.getLogger(ChangeOwnContactService.class);

    private final CommunityRepository communities;
    private final MemberRepository members;
    private final UserDirectory users;
    private final Clock clock;

    public ChangeOwnContactService(CommunityRepository communities, MemberRepository members, UserDirectory users,
            Clock clock) {
        this.communities = communities;
        this.members = members;
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OwnMemberView change(Actor actor, ContactChange change) {
        Member read = members.findByLinkedUser(actor.id())
                .orElseThrow(() -> new CommunityDomainException("MEMBER_NOT_LINKED"));
        CommunityLocks.lock(communities, read.communityId());
        Member member = members.lock(read.id()).orElseThrow(MemberNotFound::new);
        boolean stale = !member.communityId().equals(read.communityId())
                || !member.linkedUserId().equals(Optional.of(actor.id()));
        if (stale) {
            throw new StaleLockOrderException();
        }
        member.changeContact(change, Optional.of(ConsentContext.byMember(actor.id())), clock.instant());
        members.save(member);
        log.atInfo().addKeyValue("memberId", member.id().value()).log("Datos de contacto propios actualizados");
        return OwnMemberViews.of(member, communities, users);
    }
}
