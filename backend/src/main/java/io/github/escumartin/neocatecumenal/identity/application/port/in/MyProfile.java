package io.github.escumartin.neocatecumenal.identity.application.port.in;

import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PendingStep;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
import java.util.Optional;

public record MyProfile(
        UserId userId,
        AccessId accessId,
        DisplayName name,
        UserRole role,
        CommunityId communityId,
        CommunityNumber communityNumber,
        List<PendingStep> pendingSteps,
        String currentPrivacyNoticeVersion,
        Optional<PrivacyConsent> privacyConsent) {
}
