package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.application.Fakes.FakeHasher;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FixedInitialPassword;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryCommunities;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryUsers;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.SequenceAccessIds;
import io.github.escumartin.neocatecumenal.identity.application.port.in.AdministratorAlreadyExistsException;
import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.CreatedAdministrator;
import io.github.escumartin.neocatecumenal.identity.application.service.BootstrapIdentityService;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BootstrapIdentityServiceTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final FakeHasher hasher = new FakeHasher();
    private final Fakes.InMemoryProvisioner provisioner = new Fakes.InMemoryProvisioner(communities);

    private BootstrapIdentityService service(Integer... accessIds) {
        return new BootstrapIdentityService(users, hasher, new SequenceAccessIds(accessIds),
                new FixedInitialPassword(), provisioner);
    }

    private static BootstrapCommand command(int administratorCommunity) {
        return new BootstrapCommand(new DisplayName("Test Admin"), new CommunityNumber(administratorCommunity));
    }

    private void addExistingRegisteredUser(int accessId) {
        users.save(User.register(new UserId(UUID.randomUUID()),
                new AccessId(accessId), new DisplayName("Existing"), UserRole.REGISTERED,
                communities.findByNumber(new CommunityNumber(1)).orElseThrow().id(),
                hasher.hash(new PlainPassword("irrelevant"))));
    }

    @Test
    void createsExactlyOneCommunityAndTheAdministratorWhenThereIsNoAdministrator() {
        service(123456).run(command(2));

        assertThat(communities.findAll()).extracting(community -> community.number().value()).containsExactly(2);
        assertThat(users.users).hasSize(1);
    }

    @Test
    void createsTheAdministratorWithGeneratedCredentialsThatMustBeChanged() {
        CreatedAdministrator created = service(123456).run(command(2));

        assertThat(created.accessId()).isEqualTo(new AccessId(123456));
        assertThat(created.initialPassword().value()).isEqualTo("initialpass");
        User administrator = users.findByAccessId(new AccessId(123456)).orElseThrow();
        assertThat(administrator.role()).isEqualTo(UserRole.ADMINISTRATOR);
        assertThat(administrator.name()).isEqualTo(new DisplayName("Test Admin"));
        assertThat(administrator.passwordChangeRequired()).isTrue();
        assertThat(administrator.passwordHash().value()).isEqualTo("{fake}initialpass");
        assertThat(administrator.communityId())
                .isEqualTo(communities.findByNumber(new CommunityNumber(2)).orElseThrow().id());
    }

    @Test
    void failsWithoutTouchingAnythingWhenAnAdministratorAlreadyExists() {
        service(123456).run(command(1));
        int savesBefore = users.saves;
        provisioner.requested.clear();

        assertThatThrownBy(() -> service(654321).run(command(7)))
                .isInstanceOf(AdministratorAlreadyExistsException.class);

        assertThat(provisioner.requested).isEmpty();
        assertThat(users.saves).isEqualTo(savesBefore);
        assertThat(users.users).hasSize(1);
        assertThat(communities.findByNumber(new CommunityNumber(7))).isEmpty();
    }

    @Test
    void takesTheBootstrapLockBeforeCheckingForAnAdministrator() {
        service(123456).run(command(1));

        assertThat(users.calls).containsSubsequence("lockForBootstrap", "existsAdministrator");
    }

    @Test
    void requestsTheCommunityOfTheAdministratorOnlyOnce() {
        service(111111).run(command(2));

        assertThat(provisioner.requested).containsExactly(2);
    }

    @Test
    void rejectsACommandWithoutAdministratorCommunityAndCreatesNothing() {
        assertThatThrownBy(() -> new BootstrapCommand(new DisplayName("Test Admin"), null))
                .isInstanceOf(NullPointerException.class);

        assertThat(communities.findAll()).isEmpty();
        assertThat(users.users).isEmpty();
    }

    @Test
    void rejectsACommandWithoutAdministratorName() {
        assertThatThrownBy(() -> new BootstrapCommand(null, new CommunityNumber(1)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void retriesWhenTheGeneratedAccessIdIsTaken() {
        service(123456).run(command(1));
        users.users.clear();
        addExistingRegisteredUser(111111);

        CreatedAdministrator created = service(111111, 111111, 222222).run(command(1));

        assertThat(created.accessId()).isEqualTo(new AccessId(222222));
    }

    @Test
    void givesUpAfterFiveTakenAccessIds() {
        service(123456).run(command(1));
        users.users.clear();
        addExistingRegisteredUser(111111);

        assertThatThrownBy(() -> service(111111, 111111, 111111, 111111, 111111).run(command(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(users.existsAdministrator()).isFalse();
    }
}
