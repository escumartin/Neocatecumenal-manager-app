package io.github.escumartin.neocatecumenal.community.application;

import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import java.util.ArrayList;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import java.util.List;
import java.util.Optional;

final class FakeUserDirectory implements UserDirectory {

    final List<LinkedUser> users = new ArrayList<>();
    final List<CommunityId> queried = new ArrayList<>();
    final List<UserId> looked = new ArrayList<>();

    @Override
    public List<LinkedUser> usersOf(CommunityId communityId) {
        queried.add(communityId);
        return users.stream().filter(user -> user.communityId().equals(communityId)).toList();
    }

    @Override
    public Optional<LinkedUser> user(UserId id) {
        looked.add(id);
        return users.stream().filter(user -> user.id().equals(id)).findFirst();
    }
}
