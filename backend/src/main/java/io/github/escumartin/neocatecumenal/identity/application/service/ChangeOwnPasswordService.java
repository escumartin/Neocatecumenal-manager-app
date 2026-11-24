package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.ChangeOwnPassword;
import io.github.escumartin.neocatecumenal.identity.application.port.in.FieldValidationException;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSession;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordChangeAttempts;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordHasher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.CommonPasswords;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.PasswordPolicy;
import io.github.escumartin.neocatecumenal.identity.domain.PendingStep;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class ChangeOwnPasswordService implements ChangeOwnPassword {

    private static final Logger log = LoggerFactory.getLogger(ChangeOwnPasswordService.class);
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private final CurrentSessionContext currentSession;
    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final CommonPasswords commonPasswords;
    private final PrivacyNoticeSettings privacyNotice;
    private final PasswordChangeAttempts attempts;

    public ChangeOwnPasswordService(CurrentSessionContext currentSession, UserRepository users,
            SessionRepository sessions, PasswordHasher hasher, CommonPasswords commonPasswords,
            PrivacyNoticeSettings privacyNotice, PasswordChangeAttempts attempts) {
        this.currentSession = currentSession;
        this.users = users;
        this.sessions = sessions;
        this.hasher = hasher;
        this.commonPasswords = commonPasswords;
        this.privacyNotice = privacyNotice;
        this.attempts = attempts;
    }

    @Override
    @Transactional(noRollbackFor = {FieldValidationException.class, DomainException.class})
    public void change(PlainPassword currentPassword, PlainPassword newPassword) {
        CurrentSession session = currentSession.current().orElseThrow(() -> new DomainException("UNAUTHENTICATED"));
        User user = users.findById(session.userId()).orElseThrow(() -> new DomainException("UNAUTHENTICATED"));
        if (user.pendingSteps(privacyNotice.currentVersion()).contains(PendingStep.PRIVACY_NOTICE)) {
            throw new DomainException("FORBIDDEN");
        }
        if (!hasher.matches(currentPassword, user.passwordHash())) {
            rejectWrongCurrentPassword(user);
        }
        attempts.clear(user.id());
        List<String> reasons = PasswordPolicy.check(newPassword, user.accessId(), commonPasswords);
        if (!reasons.isEmpty()) {
            throw new FieldValidationException("newPassword", reasons.get(0));
        }
        user.changeOwnPassword(hasher.hash(newPassword));
        users.save(user);
        sessions.deleteAllOfExcept(user.id(), session.sessionId());
        log.atInfo().addKeyValue("userId", user.id().value()).log("Contraseña cambiada por el propio usuario");
    }

    private void rejectWrongCurrentPassword(User user) {
        if (attempts.recordFailure(user.id()) >= MAX_CONSECUTIVE_FAILURES) {
            sessions.deleteAllOf(user.id());
            attempts.clear(user.id());
            log.atWarn().addKeyValue("userId", user.id().value())
                    .log("Sesiones cerradas por exceso de contraseñas actuales incorrectas");
            throw new DomainException("UNAUTHENTICATED");
        }
        throw new FieldValidationException("currentPassword", "CURRENT_PASSWORD_INCORRECT");
    }
}
