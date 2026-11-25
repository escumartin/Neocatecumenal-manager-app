package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.ReactivateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.IdentityCommunityCatalog;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class ReactivateCommunityService implements ReactivateCommunity {

    private static final Logger log = LoggerFactory.getLogger(ReactivateCommunityService.class);

    private final CommunityRepository communities;
    private final IdentityCommunityCatalog catalog;

    public ReactivateCommunityService(CommunityRepository communities, IdentityCommunityCatalog catalog) {
        this.communities = communities;
        this.catalog = catalog;
    }

    @Override
    @Transactional
    public void reactivate(Actor actor, CommunityId id) {
        MemberAccessPolicy.requireCanManageCommunities(actor);
        Community community = CommunityLocks.lock(communities, id);
        community.reactivate();
        communities.save(community);
        catalog.setActive(id, true);
        log.atInfo().addKeyValue("communityId", id.value()).log("Comunidad reactivada");
    }
}
