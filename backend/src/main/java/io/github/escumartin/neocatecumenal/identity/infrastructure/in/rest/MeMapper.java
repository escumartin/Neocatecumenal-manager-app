package io.github.escumartin.neocatecumenal.identity.infrastructure.in.rest;

import io.github.escumartin.neocatecumenal.generated.model.Me;
import io.github.escumartin.neocatecumenal.generated.model.PendingStep;
import io.github.escumartin.neocatecumenal.generated.model.PrivacyNoticeAcceptanceRecord;
import io.github.escumartin.neocatecumenal.generated.model.Role;
import io.github.escumartin.neocatecumenal.identity.application.port.in.MyProfile;
import java.time.ZoneOffset;

final class MeMapper {

    private MeMapper() {
    }

    static Me toMe(MyProfile profile) {
        Me me = new Me(
                profile.userId().value(),
                profile.accessId().value(),
                profile.name().value(),
                Role.valueOf(profile.role().name()),
                profile.communityId().value(),
                profile.communityNumber().value(),
                profile.pendingSteps().stream().map(step -> PendingStep.valueOf(step.name())).toList(),
                profile.currentPrivacyNoticeVersion());
        profile.privacyConsent().ifPresent(consent -> me.setPrivacyNoticeAcceptance(
                new PrivacyNoticeAcceptanceRecord(consent.noticeVersion(),
                        consent.acceptedAt().atOffset(ZoneOffset.UTC))));
        return me;
    }
}
