package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.DeactivateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.IdentityCommunityCatalog;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class DeactivateCommunityService implements DeactivateCommunity {

    private static final Logger log = LoggerFactory.getLogger(DeactivateCommunityService.class);

    private final CommunityRepository communities;
    private final UserDirectory users;
    private final IdentityCommunityCatalog catalog;

    public DeactivateCommunityService(CommunityRepository communities, UserDirectory users,
            IdentityCommunityCatalog catalog) {
        this.communities = communities;
        this.users = users;
        this.catalog = catalog;
    }

    @Override
    @Transactional
    public void deactivate(Actor actor, CommunityId id, boolean confirmed) {
        MemberAccessPolicy.requireCanManageCommunities(actor);
        Community community = CommunityLocks.lock(communities, id);
        boolean privilegedUsersPresent = users.usersOf(id).stream()
                .anyMatch(user -> user.role() != ActorRole.REGISTERED);
        community.deactivate(privilegedUsersPresent, confirmed);
        communities.save(community);
        catalog.setActive(id, false);
        log.atInfo().addKeyValue("communityId", id.value()).log("Comunidad desactivada");
    }
}
