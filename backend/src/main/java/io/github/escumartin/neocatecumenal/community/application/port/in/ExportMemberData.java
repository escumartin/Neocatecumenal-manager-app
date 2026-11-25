package io.github.escumartin.neocatecumenal.community.application.port.in;

import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;

public interface ExportMemberData {

    MemberDataExport export(Actor actor, MemberId id);
}
