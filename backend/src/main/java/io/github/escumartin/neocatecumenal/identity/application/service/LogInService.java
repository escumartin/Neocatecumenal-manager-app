package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.LogIn;
import io.github.escumartin.neocatecumenal.identity.application.port.in.LoginCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.LoginResult;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.IssuedSessionToken;
import io.github.escumartin.neocatecumenal.identity.application.port.out.LoginThrottleStore;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordHasher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle;
import io.github.escumartin.neocatecumenal.identity.domain.LoginThrottle.Outcome;
import io.github.escumartin.neocatecumenal.identity.domain.Session;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

public class LogInService implements LogIn {

    private static final Logger log = LoggerFactory.getLogger(LogInService.class);

    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final SessionTokenIssuer tokens;
    private final LoginThrottleStore throttle;
    private final MyProfileAssembler profiles;
    private final Clock clock;

    public LogInService(UserRepository users, CommunityRepository communities, SessionRepository sessions,
            PasswordHasher hasher, SessionTokenIssuer tokens, LoginThrottleStore throttle,
            PrivacyNoticeSettings privacyNotice, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.hasher = hasher;
        this.tokens = tokens;
        this.throttle = throttle;
        this.profiles = new MyProfileAssembler(communities, privacyNotice);
        this.clock = clock;
    }

    @Override
    @Transactional
    public LoginResult logIn(LoginCommand command) {
        Instant now = clock.instant();
        LoginThrottle.IpState ipBefore = throttle.lockIp(command.clientIp());
        LoginThrottle.PairState pairBefore = throttle.lockPair(command.accessId(), command.clientIp());

        Outcome outcome = LoginThrottle.reserve(ipBefore, pairBefore, now);
        if (outcome instanceof Outcome.Rejected rejected) {
            log.atWarn()
                    .addKeyValue("retryAfterSeconds", rejected.retryAfter().toSeconds())
                    .log("Inicio de sesión bloqueado por exceso de intentos fallidos");
            return new LoginResult.Blocked(rejected.retryAfter());
        }
        Outcome.Reserved reserved = (Outcome.Reserved) outcome;
        throttle.save(command.clientIp(), reserved.ip());
        throttle.save(command.accessId(), command.clientIp(), reserved.pair());

        Optional<User> user = users.findByAccessId(command.accessId());
        boolean authenticated = user
                .map(found -> hasher.matches(command.password(), found.passwordHash()))
                .orElseGet(() -> hasher.matchesDummy(command.password()));
        if (!authenticated) {
            log.atWarn().addKeyValue("result", "INVALID_CREDENTIALS").log("Inicio de sesión fallido");
            return new LoginResult.InvalidCredentials();
        }

        LoginThrottle.Settled settled = LoginThrottle.onSuccess(ipBefore);
        settled.pair().ifPresentOrElse(
                pair -> throttle.save(command.accessId(), command.clientIp(), pair),
                () -> throttle.deletePair(command.accessId(), command.clientIp()));
        throttle.save(command.clientIp(), settled.ip());
        IssuedSessionToken issued = tokens.issue();
        sessions.retainMostRecentlyUsed(user.get().id(), Session.MAX_PER_USER - 1);
        sessions.save(Session.open(user.get().id(), issued.hash(), now));
        log.atInfo().addKeyValue("userId", user.get().id().value()).log("Inicio de sesión correcto");
        return new LoginResult.LoggedIn(issued.token(), profiles.assemble(user.get()));
    }
}
