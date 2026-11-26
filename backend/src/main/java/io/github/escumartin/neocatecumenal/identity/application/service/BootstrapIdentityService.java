package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.CommunityProvisioner;
import io.github.escumartin.neocatecumenal.identity.application.port.in.AdministratorAlreadyExistsException;
import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapIdentity;
import io.github.escumartin.neocatecumenal.identity.application.port.in.CreatedAdministrator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.AccessIdGenerator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.InitialPasswordGenerator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordHasher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class BootstrapIdentityService implements BootstrapIdentity {

    private static final int MAX_ACCESS_ID_ATTEMPTS = 5;

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final AccessIdGenerator accessIds;
    private final InitialPasswordGenerator passwords;
    private final CommunityProvisioner provisioner;

    public BootstrapIdentityService(UserRepository users, PasswordHasher hasher,
            AccessIdGenerator accessIds, InitialPasswordGenerator passwords,
            CommunityProvisioner provisioner) {
        this.users = users;
        this.hasher = hasher;
        this.accessIds = accessIds;
        this.passwords = passwords;
        this.provisioner = provisioner;
    }

    @Override
    @Transactional
    public CreatedAdministrator run(BootstrapCommand command) {
        users.lockForBootstrap();
        if (users.existsAdministrator()) {
            throw new AdministratorAlreadyExistsException();
        }
        CommunityId communityId = new CommunityId(provisioner.ensure(command.administratorCommunity().value()));
        return createAdministrator(command, communityId);
    }

    private CreatedAdministrator createAdministrator(BootstrapCommand command, CommunityId communityId) {
        AccessId accessId = freeAccessId();
        PlainPassword password = passwords.next();
        users.save(User.register(new UserId(UUID.randomUUID()), accessId, command.administratorName(),
                UserRole.ADMINISTRATOR, communityId, hasher.hash(password)));
        return new CreatedAdministrator(accessId, password);
    }

    private AccessId freeAccessId() {
        for (int attempt = 0; attempt < MAX_ACCESS_ID_ATTEMPTS; attempt++) {
            AccessId candidate = accessIds.next();
            if (users.findByAccessId(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se pudo generar un identificador de acceso libre");
    }
}
