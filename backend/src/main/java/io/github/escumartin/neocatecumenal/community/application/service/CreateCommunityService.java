package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.CreateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.IdentityCommunityCatalog;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityName;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class CreateCommunityService implements CreateCommunity {

    private static final Logger log = LoggerFactory.getLogger(CreateCommunityService.class);

    private final CommunityRepository communities;
    private final IdentityCommunityCatalog catalog;

    public CreateCommunityService(CommunityRepository communities, IdentityCommunityCatalog catalog) {
        this.communities = communities;
        this.catalog = catalog;
    }

    @Override
    @Transactional
    public Community create(Actor actor, CommunityNumber number, Optional<CommunityName> name) {
        MemberAccessPolicy.requireCanManageCommunities(actor);
        CommunityNumbers.requireFree(communities, number, Optional.empty());
        Community saved = communities.save(Community.create(new CommunityId(UUID.randomUUID()), number, name));
        catalog.register(saved.id(), saved.number());
        log.atInfo().addKeyValue("communityId", saved.id().value()).log("Comunidad creada");
        return saved;
    }
}
