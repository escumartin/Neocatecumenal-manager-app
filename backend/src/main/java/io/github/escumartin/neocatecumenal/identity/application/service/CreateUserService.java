package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.CreateUser;
import io.github.escumartin.neocatecumenal.identity.application.port.in.CreateUserCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.CreatedUserResult;
import io.github.escumartin.neocatecumenal.identity.application.port.in.FieldValidationException;
import io.github.escumartin.neocatecumenal.identity.application.port.out.AccessIdAlreadyTakenException;
import io.github.escumartin.neocatecumenal.identity.application.port.out.AccessIdGenerator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CurrentSessionContext;
import io.github.escumartin.neocatecumenal.identity.application.port.out.InitialPasswordGenerator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordHasher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PrivacyNoticeSettings;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.Actor;
import io.github.escumartin.neocatecumenal.identity.domain.DomainException;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserManagementPolicy;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

public class CreateUserService implements CreateUser {

    private static final Logger log = LoggerFactory.getLogger(CreateUserService.class);
    private static final int MAX_ACCESS_ID_ATTEMPTS = 5;

    private final ActorResolver actors;
    private final UserRepository users;
    private final CommunityRepository communities;
    private final PasswordHasher hasher;
    private final AccessIdGenerator accessIds;
    private final InitialPasswordGenerator passwords;
    private final TransactionOperations transactions;

    public CreateUserService(CurrentSessionContext currentSession, UserRepository users,
            CommunityRepository communities, PasswordHasher hasher, AccessIdGenerator accessIds,
            InitialPasswordGenerator passwords, PrivacyNoticeSettings privacyNotice,
            TransactionOperations transactions) {
        this.actors = new ActorResolver(currentSession, users, privacyNotice);
        this.users = users;
        this.communities = communities;
        this.hasher = hasher;
        this.accessIds = accessIds;
        this.passwords = passwords;
        this.transactions = transactions;
    }

    @Override
    public CreatedUserResult create(CreateUserCommand command) {
        for (int attempt = 0; attempt < MAX_ACCESS_ID_ATTEMPTS; attempt++) {
            try {
                return transactions.execute(status -> createOnce(command));
            } catch (AccessIdAlreadyTakenException collision) {
                log.atWarn().addKeyValue("attempt", attempt + 1).log("Identificador de acceso ya ocupado, se reintenta con otro");
            }
        }
        throw new IllegalStateException("No se pudo generar un identificador de acceso libre");
    }

    private CreatedUserResult createOnce(CreateUserCommand command) {
        Actor actor = actors.resolve();
        if (!UserManagementPolicy.canCreate(actor, command.role(), command.communityId())) {
            throw new DomainException("FORBIDDEN");
        }
        if (communities.findById(command.communityId()).isEmpty()) {
            throw new FieldValidationException("communityId", "COMMUNITY_NOT_FOUND");
        }
        PlainPassword initialPassword = passwords.next();
        User created = users.save(User.register(new UserId(UUID.randomUUID()), accessIds.next(), command.name(),
                command.role(), command.communityId(), hasher.hash(initialPassword)));
        log.atInfo()
                .addKeyValue("actorId", actor.id().value())
                .addKeyValue("userId", created.id().value())
                .addKeyValue("role", created.role())
                .log("Usuario creado");
        return new CreatedUserResult(created, initialPassword);
    }
}
