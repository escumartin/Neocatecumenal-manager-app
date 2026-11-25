package io.github.escumartin.neocatecumenal.community.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.model.CommunityDetail;
import io.github.escumartin.neocatecumenal.generated.model.CommunityStatus;
import io.github.escumartin.neocatecumenal.generated.model.MemberSummary;
import io.github.escumartin.neocatecumenal.community.application.port.in.CommunityView;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityFieldInvalid;
import io.github.escumartin.neocatecumenal.community.domain.CommunityName;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import java.util.Optional;

final class CommunitiesMapper {

    private CommunitiesMapper() {
    }

    static CommunityNumber toNumber(Integer number) {
        if (number == null) {
            throw new CommunityFieldInvalid("number", "REQUIRED");
        }
        return new CommunityNumber(number);
    }

    static Optional<CommunityName> toName(String name) {
        return CommunityName.of(name);
    }

    static io.github.escumartin.neocatecumenal.generated.model.Community toSummary(Community community) {
        return new io.github.escumartin.neocatecumenal.generated.model.Community(community.id().value(), community.number().value())
                .name(community.name().map(CommunityName::value).orElse(null))
                .status(toStatus(community))
                .hasResponsible(community.hasResponsible());
    }

    static CommunityDetail toDetail(CommunityView view) {
        CommunityDetail detail = toDetail(view.community())
                .responsibleIsModerator(view.responsibleIsModerator());
        return view.responsible().map(CommunitiesMapper::toSummary).map(detail::responsible).orElse(detail);
    }

    private static MemberSummary toSummary(Member member) {
        return new MemberSummary(member.id().value(), member.name().givenName())
                .surnames(member.name().surnames().orElse(null));
    }

    static CommunityDetail toDetail(Community community) {
        return new CommunityDetail(community.id().value(), community.number().value(), toStatus(community),
                community.hasResponsible(), false)
                .name(community.name().map(CommunityName::value).orElse(null));
    }

    private static CommunityStatus toStatus(Community community) {
        return CommunityStatus.valueOf(community.status().name());
    }
}
