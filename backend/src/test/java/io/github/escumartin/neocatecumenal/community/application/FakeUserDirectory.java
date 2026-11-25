package io.github.escumartin.neocatecumenal.community.application;

import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import java.util.ArrayList;
import java.util.List;

final class FakeUserDirectory implements UserDirectory {

    final List<LinkedUser> users = new ArrayList<>();
    final List<CommunityId> queried = new ArrayList<>();

    @Override
    public List<LinkedUser> usersOf(CommunityId communityId) {
        queried.add(communityId);
        return users.stream().filter(user -> user.communityId().equals(communityId)).toList();
    }
}
