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
    final List<UserId> locked = new ArrayList<>();
    final List<Move> moves = new ArrayList<>();
    List<String> log = new ArrayList<>();
    RuntimeException failOnMove;

    record Move(UserId user, CommunityId community) {
    }

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

    @Override
    public Optional<LinkedUser> lockUser(UserId id) {
        log.add("user-lock");
        locked.add(id);
        return users.stream().filter(user -> user.id().equals(id)).findFirst();
    }

    @Override
    public void moveUser(UserId id, CommunityId communityId) {
        log.add("user-move");
        if (failOnMove != null) {
            throw failOnMove;
        }
        moves.add(new Move(id, communityId));
    }
}
