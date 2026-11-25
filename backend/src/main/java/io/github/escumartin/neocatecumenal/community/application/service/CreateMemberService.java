package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.CreateMember;
import io.github.escumartin.neocatecumenal.community.application.port.in.MemberView;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.ConsentContext;
import io.github.escumartin.neocatecumenal.community.domain.ContactChange;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.OnBehalfConsent;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class CreateMemberService implements CreateMember {

    private static final Logger log = LoggerFactory.getLogger(CreateMemberService.class);

    private final CommunityRepository communities;
    private final MemberRepository members;
    private final Clock clock;

    public CreateMemberService(CommunityRepository communities, MemberRepository members, Clock clock) {
        this.communities = communities;
        this.members = members;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MemberView create(Actor actor, CommunityId communityId, PersonName name, Optional<ContactChange> contact,
            Optional<OnBehalfConsent> consent) {
        MemberAccessPolicy.requireCanManageMembers(actor, communityId);
        Community community = CommunityLocks.lock(communities, communityId);
        Member member = Member.register(new MemberId(UUID.randomUUID()), community, name, contact,
                consent.map(onBehalf -> ConsentContext.onBehalf(actor, onBehalf)), clock.instant());
        members.save(member);
        log.atInfo().addKeyValue("actorId", actor.id().value()).addKeyValue("memberId", member.id().value())
                .addKeyValue("communityId", communityId.value()).log("Hermano creado");
        return new MemberView(member, false);
    }
}
