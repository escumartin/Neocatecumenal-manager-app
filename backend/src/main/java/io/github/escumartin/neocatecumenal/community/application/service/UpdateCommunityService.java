package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.UpdateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.IdentityCommunityCatalog;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityName;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class UpdateCommunityService implements UpdateCommunity {

    private static final Logger log = LoggerFactory.getLogger(UpdateCommunityService.class);

    private final CommunityRepository communities;
    private final IdentityCommunityCatalog catalog;

    public UpdateCommunityService(CommunityRepository communities, IdentityCommunityCatalog catalog) {
        this.communities = communities;
        this.catalog = catalog;
    }

    @Override
    @Transactional
    public Community update(Actor actor, CommunityId id, CommunityNumber number, Optional<CommunityName> name) {
        MemberAccessPolicy.requireCanManageCommunities(actor);
        Community community = CommunityLocks.lock(communities, id);
        CommunityNumbers.requireFree(communities, number, Optional.of(id));
        community.renumber(number);
        community.rename(name);
        Community saved = communities.save(community);
        catalog.renumber(saved.id(), saved.number());
        log.atInfo().addKeyValue("communityId", saved.id().value()).log("Comunidad actualizada");
        return saved;
    }
}
