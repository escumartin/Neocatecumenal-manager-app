package io.github.escumartin.neocatecumenal.identity.application.port.out;

import io.github.escumartin.neocatecumenal.identity.application.port.in.UserPage;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByAccessId(AccessId accessId);

    boolean existsAdministrator();

    int lockAdministrators();

    void lockCommunity(CommunityId communityId);

    long countModerators(CommunityId communityId);

    UserPage findByCommunity(CommunityId communityId, int page, int size);

    void delete(UserId id);
}
