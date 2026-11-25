package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.application.Fakes.FakeHasher;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FixedInitialPassword;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryCommunities;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryUsers;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.SequenceAccessIds;
import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapCommand;
import io.github.escumartin.neocatecumenal.identity.application.port.in.BootstrapOutcome;
import io.github.escumartin.neocatecumenal.identity.application.service.BootstrapIdentityService;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BootstrapIdentityServiceTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final FakeHasher hasher = new FakeHasher();
    private final Fakes.InMemoryProvisioner provisioner = new Fakes.InMemoryProvisioner(communities);

    private BootstrapIdentityService service(Integer... accessIds) {
        return new BootstrapIdentityService(users, communities, hasher, new SequenceAccessIds(accessIds),
                new FixedInitialPassword(), provisioner);
    }

    private static BootstrapCommand command(int administratorCommunity, int... numbers) {
        return new BootstrapCommand(
                Arrays.stream(numbers).mapToObj(CommunityNumber::new).toList(),
                new DisplayName("Test Admin"),
                new CommunityNumber(administratorCommunity));
    }

    @Test
    void createsEveryCommunityOfTheList() {
        BootstrapOutcome outcome = service(111111).run(command(1, 1, 2, 3));

        assertThat(communities.findAll()).extracting(community -> community.number().value()).containsExactly(1, 2, 3);
        assertThat(outcome.createdCommunities()).isEqualTo(3);
    }

    @Test
    void createsTheAdministratorCommunityEvenWhenItIsNotInTheList() {
        service(111111).run(command(9, 1, 2));

        assertThat(communities.findByNumber(new CommunityNumber(9))).isPresent();
    }

    @Test
    void createsOnlyTheMissingCommunities() {
        service(111111).run(command(1, 1, 2));

        BootstrapOutcome outcome = service(222222).run(command(1, 1, 2, 3));

        assertThat(communities.findAll()).hasSize(3);
        assertThat(outcome.createdCommunities()).isEqualTo(1);
    }

    @Test
    void createsAnAdministratorWithGeneratedCredentialsThatMustBeChanged() {
        BootstrapOutcome outcome = service(123456).run(command(2, 1, 2));

        assertThat(outcome.administrator()).isPresent();
        assertThat(outcome.administrator().get().accessId()).isEqualTo(new AccessId(123456));
        assertThat(outcome.administrator().get().initialPassword().value()).isEqualTo("initialpass");
        User administrator = users.findByAccessId(new AccessId(123456)).orElseThrow();
        assertThat(administrator.role()).isEqualTo(UserRole.ADMINISTRATOR);
        assertThat(administrator.name()).isEqualTo(new DisplayName("Test Admin"));
        assertThat(administrator.passwordChangeRequired()).isTrue();
        assertThat(administrator.passwordHash().value()).isEqualTo("{fake}initialpass");
        assertThat(administrator.communityId())
                .isEqualTo(communities.findByNumber(new CommunityNumber(2)).orElseThrow().id());
    }

    @Test
    void doesNotCreateAnotherAdministratorWhenOneExists() {
        service(123456).run(command(1, 1));

        BootstrapOutcome outcome = service(654321).run(command(1, 1));

        assertThat(outcome.administrator()).isEmpty();
        assertThat(users.users).hasSize(1);
    }

    @Test
    void retriesWhenTheGeneratedAccessIdIsTaken() {
        service(123456).run(command(1, 1));
        users.users.clear();
        users.save(User.register(new UserId(UUID.randomUUID()),
                new AccessId(111111), new DisplayName("Existing"), UserRole.REGISTERED,
                communities.findByNumber(new CommunityNumber(1)).orElseThrow().id(),
                hasher.hash(new PlainPassword("irrelevant"))));

        BootstrapOutcome outcome = service(111111, 111111, 222222).run(command(1, 1));

        assertThat(outcome.administrator().orElseThrow().accessId()).isEqualTo(new AccessId(222222));
    }

    @Test
    void givesUpAfterFiveTakenAccessIds() {
        service(123456).run(command(1, 1));
        users.users.clear();
        users.save(User.register(new UserId(UUID.randomUUID()),
                new AccessId(111111), new DisplayName("Existing"), UserRole.REGISTERED,
                communities.findByNumber(new CommunityNumber(1)).orElseThrow().id(),
                hasher.hash(new PlainPassword("irrelevant"))));

        assertThatThrownBy(() -> service(111111, 111111, 111111, 111111, 111111).run(command(1, 1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(users.existsAdministrator()).isFalse();
    }

    @Test
    void emptyCommunityListStillCreatesTheAdministratorCommunity() {
        BootstrapOutcome outcome = service(123456).run(new BootstrapCommand(List.of(), new DisplayName("Test Admin"),
                new CommunityNumber(4)));

        assertThat(outcome.createdCommunities()).isEqualTo(1);
        assertThat(outcome.administrator()).isPresent();
    }

    @Test
    void asksTheProvisionerOncePerDistinctCommunityNumber() {
        service(111111).run(command(2, 1, 2, 3));

        assertThat(provisioner.requested).containsExactly(1, 2, 3);
    }

    @Test
    void createsTheAdministratorInTheCommunityTheProvisionerReturns() {
        BootstrapOutcome outcome = service(123456).run(command(5, 5));

        User administrator = users.findByAccessId(new AccessId(123456)).orElseThrow();
        assertThat(outcome.administrator()).isPresent();
        assertThat(administrator.communityId())
                .isEqualTo(communities.findByNumber(new CommunityNumber(5)).orElseThrow().id());
    }
}
