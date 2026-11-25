package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.ListCommunities;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityStatus;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class ListCommunitiesService implements ListCommunities {

    private final CommunityRepository communities;

    public ListCommunitiesService(CommunityRepository communities) {
        this.communities = communities;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Community> list(Actor actor, boolean includeDeactivated) {
        if (includeDeactivated) {
            MemberAccessPolicy.requireCanManageCommunities(actor);
            return communities.findAll();
        }
        return communities.findAll().stream().filter(community -> community.status() == CommunityStatus.ACTIVE)
                .toList();
    }
}
