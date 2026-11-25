package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.domain.CommunityFieldInvalid;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import java.util.Optional;

final class CommunityNumbers {

    private CommunityNumbers() {
    }

    static void requireFree(CommunityRepository communities, CommunityNumber number, Optional<CommunityId> owner) {
        boolean taken = communities.findByNumber(number)
                .filter(other -> owner.map(id -> !id.equals(other.id())).orElse(true))
                .isPresent();
        if (taken) {
            throw new CommunityFieldInvalid("number", "COMMUNITY_NUMBER_TAKEN");
        }
    }
}
