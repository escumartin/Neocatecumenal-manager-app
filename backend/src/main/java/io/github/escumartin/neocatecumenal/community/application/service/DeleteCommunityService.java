package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.DeleteCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityHistory;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.IdentityCommunityCatalog;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class DeleteCommunityService implements DeleteCommunity {

    private static final Logger log = LoggerFactory.getLogger(DeleteCommunityService.class);

    private final CommunityRepository communities;
    private final MemberRepository members;
    private final UserDirectory users;
    private final CommunityHistory history;
    private final IdentityCommunityCatalog catalog;

    public DeleteCommunityService(CommunityRepository communities, MemberRepository members, UserDirectory users,
            CommunityHistory history, IdentityCommunityCatalog catalog) {
        this.communities = communities;
        this.members = members;
        this.users = users;
        this.history = history;
        this.catalog = catalog;
    }

    @Override
    @Transactional
    public void delete(Actor actor, CommunityId id) {
        MemberAccessPolicy.requireCanManageCommunities(actor);
        Community community = CommunityLocks.lock(communities, id);
        community.checkDeletable(members.countIn(id), users.usersOf(id).size(), history.hasHistory(id));
        communities.delete(id);
        catalog.remove(id);
        log.atInfo().addKeyValue("communityId", id.value()).log("Comunidad borrada");
    }
}
