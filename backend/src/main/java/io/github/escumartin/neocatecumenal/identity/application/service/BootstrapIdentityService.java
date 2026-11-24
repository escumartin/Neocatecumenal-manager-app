package io.github.escumartin.neocatecumenal.identity.application.service;

import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapIdentity;
import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapOutcome;
import io.github.escumartin.neocatecumenal.identity.application.port.in.CreatedAdministrator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.AccessIdGenerator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.InitialPasswordGenerator;
import io.github.escumartin.neocatecumenal.identity.application.port.out.PasswordHasher;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class BootstrapIdentityService implements BootstrapIdentity {

    private static final int MAX_ACCESS_ID_ATTEMPTS = 5;

    private final UserRepository users;
    private final CommunityRepository communities;
    private final PasswordHasher hasher;
    private final AccessIdGenerator accessIds;
    private final InitialPasswordGenerator passwords;

    public BootstrapIdentityService(UserRepository users, CommunityRepository communities, PasswordHasher hasher,
            AccessIdGenerator accessIds, InitialPasswordGenerator passwords) {
        this.users = users;
        this.communities = communities;
        this.hasher = hasher;
        this.accessIds = accessIds;
        this.passwords = passwords;
    }

    @Override
    @Transactional
    public BootstrapOutcome run(BootstrapCommand command) {
        Set<CommunityNumber> numbers = new LinkedHashSet<>(command.communityNumbers());
        numbers.add(command.administratorCommunity());
        int created = 0;
        for (CommunityNumber number : numbers) {
            if (communities.findByNumber(number).isEmpty()) {
                communities.save(new Community(new CommunityId(UUID.randomUUID()), number));
                created++;
            }
        }
        Optional<CreatedAdministrator> administrator = users.existsAdministrator()
                ? Optional.empty()
                : Optional.of(createAdministrator(command));
        return new BootstrapOutcome(created, administrator);
    }

    private CreatedAdministrator createAdministrator(BootstrapCommand command) {
        Community community = communities.findByNumber(command.administratorCommunity()).orElseThrow();
        AccessId accessId = freeAccessId();
        PlainPassword password = passwords.next();
        users.save(User.register(new UserId(UUID.randomUUID()), accessId, command.administratorName(),
                UserRole.ADMINISTRATOR, community.id(), hasher.hash(password)));
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
