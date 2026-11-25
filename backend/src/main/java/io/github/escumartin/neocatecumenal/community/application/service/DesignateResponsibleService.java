package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.CommunityView;
import io.github.escumartin.neocatecumenal.community.application.port.in.DesignateResponsible;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.MemberNotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class DesignateResponsibleService implements DesignateResponsible {

    private static final Logger log = LoggerFactory.getLogger(DesignateResponsibleService.class);

    private final CommunityRepository communities;
    private final MemberRepository members;
    private final UserDirectory users;

    public DesignateResponsibleService(CommunityRepository communities, MemberRepository members,
            UserDirectory users) {
        this.communities = communities;
        this.members = members;
        this.users = users;
    }

    @Override
    @Transactional
    public CommunityView designate(Actor actor, CommunityId communityId, MemberId memberId) {
        MemberAccessPolicy.requireCanDesignateResponsible(actor);
        Community community = CommunityLocks.lock(communities, communityId);
        Member member = members.lock(memberId).orElseThrow(MemberNotFound::new);
        community.designateResponsible(member);
        communities.save(community);
        log.atInfo().addKeyValue("actorId", actor.id().value()).addKeyValue("communityId", communityId.value())
                .addKeyValue("memberId", memberId.value()).log("Responsable de la comunidad designado");
        return CommunityViews.of(community, members, users);
    }
}
