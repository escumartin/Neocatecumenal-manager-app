package io.github.escumartin.neocatecumenal.community.application.port.out;

import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;

public interface IdentityCommunityCatalog {

    void register(CommunityId id, CommunityNumber number);

    void renumber(CommunityId id, CommunityNumber number);

    void setActive(CommunityId id, boolean active);

    void remove(CommunityId id);
}
