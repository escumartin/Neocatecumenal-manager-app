package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.UserLinkGuard;
import java.util.UUID;

public class PermissiveUserLinkGuard implements UserLinkGuard {

    @Override
    public boolean allowsCommunityChange(UUID userId, UUID currentCommunityId, UUID targetCommunityId) {
        return true;
    }

    @Override
    public void releaseBeforeDeletion(UUID userId, UUID currentCommunityId) {
    }
}
