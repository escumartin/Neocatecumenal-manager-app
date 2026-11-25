package io.github.escumartin.neocatecumenal.community.application.port.out;

import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import java.util.List;
import java.util.Optional;

public interface UserDirectory {

    List<LinkedUser> usersOf(CommunityId communityId);

    Optional<LinkedUser> user(UserId id);

    Optional<LinkedUser> lockUser(UserId id);

    void moveUser(UserId id, CommunityId communityId);
}
