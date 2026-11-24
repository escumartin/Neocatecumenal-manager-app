package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.ResolvePendingSteps;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.PendingStep;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class ResolvePendingStepsService implements ResolvePendingSteps {

    private final CurrentSessionContext currentSession;
    private final UserRepository users;
    private final PrivacyNoticeSettings privacyNotice;

    public ResolvePendingStepsService(CurrentSessionContext currentSession, UserRepository users,
            PrivacyNoticeSettings privacyNotice) {
        this.currentSession = currentSession;
        this.users = users;
        this.privacyNotice = privacyNotice;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingStep> ofCurrentSession() {
        return currentSession.current()
                .flatMap(session -> users.findById(session.userId()))
                .map(user -> user.pendingSteps(privacyNotice.currentVersion()))
                .orElse(List.of());
    }
}
