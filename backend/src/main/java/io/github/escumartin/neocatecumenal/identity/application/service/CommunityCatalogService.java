package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.CommunityCatalog;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class CommunityCatalogService implements CommunityCatalog {

    private final CommunityRepository communities;

    public CommunityCatalogService(CommunityRepository communities) {
        this.communities = communities;
    }

    @Override
    @Transactional
    public void register(UUID id, int number) {
        communities.save(new Community(new CommunityId(id), new CommunityNumber(number), true));
    }

    @Override
    @Transactional
    public void renumber(UUID id, int number) {
        communities.renumber(new CommunityId(id), new CommunityNumber(number));
    }

    @Override
    @Transactional
    public void setActive(UUID id, boolean active) {
        communities.setActive(new CommunityId(id), active);
    }

    @Override
    @Transactional
    public void remove(UUID id) {
        communities.delete(new CommunityId(id));
    }
}
