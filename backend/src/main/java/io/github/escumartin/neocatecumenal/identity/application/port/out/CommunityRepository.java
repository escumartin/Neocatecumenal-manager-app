package io.github.escumartin.neocatecumenal.identity.application.port.out;

import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import java.util.List;
import java.util.Optional;

public interface CommunityRepository {

    Community save(Community community);

    Optional<Community> findById(CommunityId id);

    Optional<Community> findByNumber(CommunityNumber number);

    List<Community> findAll();
}
