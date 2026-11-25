package io.github.escumartin.neocatecumenal.community.application.service;

import io.github.escumartin.neocatecumenal.community.application.port.in.ConsentRecorder;
import io.github.escumartin.neocatecumenal.community.application.port.in.MemberView;
import io.github.escumartin.neocatecumenal.community.application.port.in.OwnConsent;
import io.github.escumartin.neocatecumenal.community.application.port.in.OwnMemberView;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.ConsentRecord;
import io.github.escumartin.neocatecumenal.community.domain.ContactField;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.RecorderRole;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class OwnMemberViews {

    private OwnMemberViews() {
    }

    static OwnMemberView of(Member member, CommunityRepository communities, UserDirectory users) {
        Map<ContactField, ConsentRecord> latest = new EnumMap<>(ContactField.class);
        for (ConsentRecord record : member.consents()) {
            latest.merge(record.field(), record,
                    (kept, candidate) -> candidate.recordedAt().isBefore(kept.recordedAt()) ? kept : candidate);
        }
        List<OwnConsent> consents = new ArrayList<>();
        latest.values().forEach(record -> consents.add(new OwnConsent(record, recorderOf(record, users))));
        return new OwnMemberView(new MemberView(member, MemberLocks.isResponsible(communities, member)), consents);
    }

    private static ConsentRecorder recorderOf(ConsentRecord record, UserDirectory users) {
        if (record.recorderRole() == RecorderRole.MEMBER) {
            return new ConsentRecorder(RecorderRole.MEMBER, Optional.empty(), false);
        }
        Optional<LinkedUser> author = users.user(record.recordedBy());
        return new ConsentRecorder(record.recorderRole(), author.map(LinkedUser::name), author.isEmpty());
    }
}
