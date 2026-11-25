package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.ChangeMemberContact;
import io.github.escumartin.neocatecumenal.community.application.port.in.MemberView;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ConsentContext;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.OnBehalfConsent;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class ChangeMemberContactService implements ChangeMemberContact {

    private static final Logger log = LoggerFactory.getLogger(ChangeMemberContactService.class);

    private final CommunityRepository communities;
    private final MemberRepository members;
    private final Clock clock;

    public ChangeMemberContactService(CommunityRepository communities, MemberRepository members, Clock clock) {
        this.communities = communities;
        this.members = members;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MemberView change(Actor actor, MemberId id, ContactChange change, Optional<OnBehalfConsent> consent) {
        Member member = MemberLocks.lockedForManagement(actor, communities, members, id);
        int consentsBefore = member.consents().size();
        member.changeContact(change, consent.map(onBehalf -> ConsentContext.onBehalf(actor, onBehalf)),
                clock.instant());
        members.save(member);
        log.atInfo().addKeyValue("actorId", actor.id().value()).addKeyValue("memberId", id.value())
                .addKeyValue("consentRecorded", member.consents().size() > consentsBefore)
                .addKeyValue("fields", change.fieldNames()).log("Contacto de hermano actualizado");
        return new MemberView(member, MemberLocks.isResponsible(communities, member));
    }
}
