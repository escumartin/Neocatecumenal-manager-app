package io.github.escumartin.neocatecumenal.spaces.infrastructure.out.identity;

import io.github.escumartin.neocatecumenal.spaces.application.port.out.CommunityLock;
import io.github.escumartin.neocatecumenal.spaces.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import org.springframework.stereotype.Component;

@Component("spacesCommunityLock")
public class IdentityCommunityLockAdapter implements CommunityLock {

    private final IdentityApi identity;

    IdentityCommunityLockAdapter(IdentityApi identity) {
        this.identity = identity;
    }

    @Override
    public boolean lockForShare(CommunityId id) {
        return identity.lockCommunityForShare(id.value());
    }
}
