package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.CommunityRef;
import io.github.escumartin.neocatecumenal.identity.application.port.in.ListCommunities;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class ListCommunitiesService implements ListCommunities {

    private final CommunityRepository communities;

    public ListCommunitiesService(CommunityRepository communities) {
        this.communities = communities;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommunityRef> list() {
        return communities.findAll().stream()
                .map(community -> new CommunityRef(community.id().value(), community.number().value()))
                .toList();
    }
}
