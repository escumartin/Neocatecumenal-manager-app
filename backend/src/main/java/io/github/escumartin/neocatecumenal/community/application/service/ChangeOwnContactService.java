package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.ChangeOwnContact;
import io.github.escumartin.neocatecumenal.community.application.port.in.OwnMemberView;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.ErasureRecords;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.ConsentContext;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberNotFound;
import io.github.escumartin.neocatecumenal.identity.StaleLockOrderException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class ChangeOwnContactService implements ChangeOwnContact {

    private static final Logger log = LoggerFactory.getLogger(ChangeOwnContactService.class);

    private final CommunityRepository communities;
    private final MemberRepository members;
    private final UserDirectory users;
    private final ErasureRecords erasures;
    private final Clock clock;

    public ChangeOwnContactService(CommunityRepository communities, MemberRepository members, UserDirectory users,
            ErasureRecords erasures, Clock clock) {
        this.communities = communities;
        this.members = members;
        this.users = users;
        this.erasures = erasures;
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
        int consentsBefore = member.consents().size();
        Set<ContactField> valuedBefore = ContactErasures.valuedFieldsOf(member);
        Instant now = clock.instant();
        member.changeContact(change, Optional.of(ConsentContext.byMember(actor.id())), now);
        members.save(member);
        ContactErasures.recordWithdrawn(erasures, member, valuedBefore, now);
        log.atInfo().addKeyValue("consentRecorded", member.consents().size() > consentsBefore)
                .addKeyValue("fields", change.fieldNames()).log("Datos de contacto propios actualizados");
        return OwnMemberViews.of(member, communities, users);
    }
}
