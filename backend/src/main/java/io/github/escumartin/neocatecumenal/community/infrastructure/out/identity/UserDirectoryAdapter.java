package io.github.escumartin.neocatecumenal.community.infrastructure.out.identity;

import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import io.github.escumartin.neocatecumenal.identity.UserRef;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UserDirectoryAdapter implements UserDirectory {

    private final IdentityApi identity;

    UserDirectoryAdapter(IdentityApi identity) {
        this.identity = identity;
    }

    @Override
    public List<LinkedUser> usersOf(CommunityId communityId) {
        return identity.usersOf(communityId.value()).stream().map(UserDirectoryAdapter::toLinkedUser).toList();
    }

    @Override
    public Optional<LinkedUser> user(UserId id) {
        return identity.user(id.value()).map(UserDirectoryAdapter::toLinkedUser);
    }

    private static LinkedUser toLinkedUser(UserRef user) {
        return new LinkedUser(new UserId(user.id()), ActorRole.valueOf(user.role().name()),
                new CommunityId(user.communityId()));
    }
}
