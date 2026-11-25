package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.ExportMemberData;
import io.github.escumartin.neocatecumenal.community.application.port.in.MemberDataExport;
import io.github.escumartin.neocatecumenal.community.application.port.in.MemberView;
import io.github.escumartin.neocatecumenal.community.application.port.in.OwnConsent;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberAccessPolicy;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class ExportMemberDataService implements ExportMemberData {

    private static final Logger log = LoggerFactory.getLogger(ExportMemberDataService.class);

    private final CommunityRepository communities;
    private final MemberRepository members;
    private final UserDirectory users;
    private final Clock clock;

    public ExportMemberDataService(CommunityRepository communities, MemberRepository members, UserDirectory users,
            Clock clock) {
        this.communities = communities;
        this.members = members;
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MemberDataExport export(Actor actor, MemberId id) {
        Member member = MemberLocks.visibleToManager(actor, members, id);
        Community community = communities.findById(member.communityId()).orElseThrow(CommunityNotFound::new);
        MemberAccessPolicy.requireDirectoryAvailable(actor, community.status());
        List<OwnConsent> consents = member.consents().stream()
                .map(record -> new OwnConsent(record, OwnMemberViews.recorderOf(record, users))).toList();
        log.atInfo().addKeyValue("actorId", actor.id().value()).addKeyValue("memberId", id.value())
                .log("Copia de datos del hermano generada");
        return new MemberDataExport(new MemberView(member, community.isResponsible(member.id())),
                community.number().value(), consents, clock.instant());
    }
}
