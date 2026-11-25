package io.github.escumartin.neocatecumenal.identity;

import java.util.UUID;

public interface UserLinkGuard {

    boolean allowsCommunityChange(UUID userId, UUID currentCommunityId, UUID targetCommunityId);

    void releaseBeforeDeletion(UUID userId, UUID currentCommunityId);
}
