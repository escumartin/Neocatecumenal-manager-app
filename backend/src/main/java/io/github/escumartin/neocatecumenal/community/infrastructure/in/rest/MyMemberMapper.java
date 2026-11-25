package io.github.escumartin.neocatecumenal.community.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.community.application.port.in.ConsentRecorder;
import io.github.escumartin.neocatecumenal.community.application.port.in.OwnConsent;
import io.github.escumartin.neocatecumenal.community.application.port.in.OwnMemberView;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ConsentRecord;
import io.github.escumartin.neocatecumenal.generated.model.ConsentChannel;
import io.github.escumartin.neocatecumenal.generated.model.MyMember;
import java.time.ZoneOffset;

final class MyMemberMapper {

    private MyMemberMapper() {
    }

    static MyMember toApi(OwnMemberView view, Actor actor) {
        return new MyMember(MembersMapper.toApi(view.member(), actor),
                view.currentConsents().stream().map(MyMemberMapper::toApi).toList());
    }

    private static io.github.escumartin.neocatecumenal.generated.model.ConsentRecord toApi(OwnConsent consent) {
        ConsentRecord record = consent.record();
        return new io.github.escumartin.neocatecumenal.generated.model.ConsentRecord(
                io.github.escumartin.neocatecumenal.generated.model.ConsentRecord.FieldEnum
                        .valueOf(record.field().name()),
                io.github.escumartin.neocatecumenal.generated.model.ConsentRecord.DecisionEnum
                        .valueOf(record.decision().name()),
                ConsentChannel.valueOf(record.channel().name()), toApi(consent.recordedBy()),
                record.noticeGiven(), record.recordedAt().atOffset(ZoneOffset.UTC));
    }

    private static io.github.escumartin.neocatecumenal.generated.model.ConsentRecorder toApi(
            ConsentRecorder recorder) {
        return new io.github.escumartin.neocatecumenal.generated.model.ConsentRecorder(
                io.github.escumartin.neocatecumenal.generated.model.ConsentRecorder.RoleEnum
                        .valueOf(recorder.role().name()),
                recorder.deleted()).name(recorder.name().orElse(null));
    }
}
