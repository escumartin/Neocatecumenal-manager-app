package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.UserId;

public interface GuardUserLink {

    boolean allowsCommunityChange(UserId userId, CommunityId current, CommunityId target);

    void releaseBeforeDeletion(UserId userId, CommunityId current);
}
