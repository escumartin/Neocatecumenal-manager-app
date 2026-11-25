package io.github.escumartin.neocatecumenal.community.application.port.out;

import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import java.util.List;

public interface UserDirectory {

    List<LinkedUser> usersOf(CommunityId communityId);
}
