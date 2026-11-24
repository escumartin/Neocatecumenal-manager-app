package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.ResetUserPassword;
import io.github.escumartin.neocatecumenal.identity.application.port.in.UserNotFoundException;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.InitialPasswordGenerator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordHasher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Actor;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserManagementPolicy;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class ResetUserPasswordService implements ResetUserPassword {

    private static final Logger log = LoggerFactory.getLogger(ResetUserPasswordService.class);

    private final ActorResolver actors;
    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final InitialPasswordGenerator passwords;

    public ResetUserPasswordService(CurrentSessionContext currentSession, UserRepository users,
            SessionRepository sessions, PasswordHasher hasher, InitialPasswordGenerator passwords,
            PrivacyNoticeSettings privacyNotice) {
        this.actors = new ActorResolver(currentSession, users, privacyNotice);
        this.users = users;
        this.sessions = sessions;
        this.hasher = hasher;
        this.passwords = passwords;
    }

    @Override
    @Transactional
    public PlainPassword reset(UserId userId) {
        Actor actor = actors.resolve();
        if (actor.role() == UserRole.REGISTERED) {
            throw new DomainException("FORBIDDEN");
        }
        User target = users.findById(userId).orElseThrow(UserNotFoundException::new);
        UserManagementPolicy.checkPasswordReset(actor, target);
        PlainPassword initialPassword = passwords.next();
        target.resetPassword(hasher.hash(initialPassword));
        users.save(target);
        sessions.deleteAllOf(target.id());
        log.atInfo()
                .addKeyValue("actorId", actor.id().value())
                .addKeyValue("userId", target.id().value())
                .log("Contraseña inicial reasignada");
        return initialPassword;
    }
}
