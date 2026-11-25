package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.ProvisionCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.IdentityCommunityCatalog;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class ProvisionCommunityService implements ProvisionCommunity {

    private static final Logger log = LoggerFactory.getLogger(ProvisionCommunityService.class);

    private final CommunityRepository communities;
    private final IdentityCommunityCatalog catalog;

    public ProvisionCommunityService(CommunityRepository communities, IdentityCommunityCatalog catalog) {
        this.communities = communities;
        this.catalog = catalog;
    }

    @Override
    @Transactional
    public CommunityId ensure(CommunityNumber number) {
        return communities.findByNumber(number).map(Community::id).orElseGet(() -> create(number));
    }

    private CommunityId create(CommunityNumber number) {
        Community saved = communities.save(Community.create(new CommunityId(UUID.randomUUID()), number,
                Optional.empty()));
        catalog.register(saved.id(), saved.number());
        log.atInfo().addKeyValue("communityId", saved.id().value()).log("Comunidad aprovisionada");
        return saved.id();
    }
}
