package io.github.escumartin.neocatecumenal.community.application.port.out;

import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import java.util.List;
import java.util.Optional;

public interface CommunityRepository {

    Community save(Community community);

    Optional<Community> findById(CommunityId id);

    Optional<Community> findByNumber(CommunityNumber number);

    List<Community> findAll();

    List<Community> lockInIdOrder(CommunityId... ids);

    void delete(CommunityId id);
}
