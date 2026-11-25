package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.CommunityView;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import java.util.Optional;

final class CommunityViews {

    private CommunityViews() {
    }

    static CommunityView of(Community community, MemberRepository members, UserDirectory users) {
        Optional<Member> responsible = community.responsibleMemberId().flatMap(members::findById);
        boolean moderator = responsible.flatMap(Member::linkedUserId).flatMap(users::user)
                .map(LinkedUser::role).filter(role -> role == ActorRole.MODERATOR).isPresent();
        return new CommunityView(community, responsible, moderator);
    }
}
