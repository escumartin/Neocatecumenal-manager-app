package io.github.escumartin.neocatecumenal.identity.domain;

public interface MembershipCounts {

    int administrators();

    long moderatorsIn(CommunityId communityId);
}
