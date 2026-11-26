package io.github.escumartin.neocatecumenal.agenda.infrastructure.out.identity;

import io.github.escumartin.neocatecumenal.agenda.application.port.out.CommunityLock;
import io.github.escumartin.neocatecumenal.agenda.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import org.springframework.stereotype.Component;

@Component("agendaCommunityLock")
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
