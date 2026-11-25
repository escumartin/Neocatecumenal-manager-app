package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;

public interface UpdateUser {

    User update(UpdateUserCommand command);

    User moveToCommunity(UserId userId, CommunityId communityId);
}
