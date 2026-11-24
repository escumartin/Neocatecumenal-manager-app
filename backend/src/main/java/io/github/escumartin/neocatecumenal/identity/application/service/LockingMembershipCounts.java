package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.MembershipCounts;

final class LockingMembershipCounts implements MembershipCounts {

    private final UserRepository users;

    LockingMembershipCounts(UserRepository users) {
        this.users = users;
    }

    @Override
    public int administrators() {
        return users.lockAdministrators();
    }

    @Override
    public long moderatorsIn(CommunityId communityId) {
        users.lockCommunity(communityId);
        return users.countModerators(communityId);
    }
}
