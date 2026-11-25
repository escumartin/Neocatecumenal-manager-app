package io.github.escumartin.neocatecumenal.community.infrastructure.in.identity;

import io.github.escumartin.neocatecumenal.community.application.port.in.GuardUserLink;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.UserLinkGuard;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class UserLinkGuardAdapter implements UserLinkGuard {

    private final GuardUserLink guard;

    UserLinkGuardAdapter(GuardUserLink guard) {
        this.guard = guard;
    }

    @Override
    public boolean allowsCommunityChange(UUID userId, UUID currentCommunityId, UUID targetCommunityId) {
        return guard.allowsCommunityChange(new UserId(userId), new CommunityId(currentCommunityId),
                new CommunityId(targetCommunityId));
    }

    @Override
    public void releaseBeforeDeletion(UUID userId, UUID currentCommunityId) {
        guard.releaseBeforeDeletion(new UserId(userId), new CommunityId(currentCommunityId));
    }
}
