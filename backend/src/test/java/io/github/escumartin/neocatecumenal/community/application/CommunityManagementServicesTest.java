package io.github.escumartin.neocatecumenal.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.service.CreateCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.DeactivateCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.ReactivateCommunityService;
import io.github.escumartin.neocatecumenal.community.application.service.UpdateCommunityService;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityFieldInvalid;
import io.github.escumartin.neocatecumenal.community.domain.CommunityForbidden;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityName;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNotFound;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.CommunityStatus;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.annotation.Transactional;

class CommunityManagementServicesTest {

    private final InMemoryCommunities communities = new InMemoryCommunities();
    private final RecordingCatalog catalog = new RecordingCatalog();
    private final FakeUserDirectory users = new FakeUserDirectory();
    private final CreateCommunityService create = new CreateCommunityService(communities, catalog);
    private final UpdateCommunityService update = new UpdateCommunityService(communities, catalog);
    private final DeactivateCommunityService deactivate = new DeactivateCommunityService(communities, users, catalog);
    private final ReactivateCommunityService reactivate = new ReactivateCommunityService(communities, catalog);
    private final Actor administrator = actor(ActorRole.ADMINISTRATOR);

    private static Actor actor(ActorRole role) {
        return new Actor(new UserId(UUID.randomUUID()), role, new CommunityId(UUID.randomUUID()));
    }

    private Community existing(int number) {
        return communities.add(Community.create(new CommunityId(UUID.randomUUID()), new CommunityNumber(number),
                Optional.empty()));
    }

    private static void assertNumberTaken(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(CommunityFieldInvalid.class, e -> {
            assertThat(e.field()).isEqualTo("number");
            assertThat(e.code()).isEqualTo("COMMUNITY_NUMBER_TAKEN");
        });
    }

    @Test
    void createWritesTheCommunityAndItsIdentityProjection() {
        Community created = create.create(administrator, new CommunityNumber(12), CommunityName.of("San Pedro"));

        assertThat(communities.stored).containsKey(created.id());
        assertThat(created.status()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(created.hasResponsible()).isFalse();
        assertThat(catalog.calls).containsExactly("register:" + created.id().value() + ":12");
    }

    @Test
    void createWithATakenNumberFailsOnTheNumberFieldAndWritesNothing() {
        existing(12);

        assertNumberTaken(() -> create.create(administrator, new CommunityNumber(12), Optional.empty()));
        assertThat(communities.stored).hasSize(1);
        assertThat(catalog.calls).isEmpty();
    }

    @Test
    void createDoesNotWriteTheProjectionWhenTheCommunityWriteFails() {
        communities.failOnSave = new IllegalStateException("write failed");

        assertThatThrownBy(() -> create.create(administrator, new CommunityNumber(5), Optional.empty()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(catalog.calls).isEmpty();
    }

    @Test
    void createPropagatesAProjectionFailureSoTheTransactionRollsBack() {
        catalog.failure = new IllegalStateException("projection failed");

        assertThatThrownBy(() -> create.create(administrator, new CommunityNumber(5), Optional.empty()))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void createIsForbiddenForNonAdministrators(ActorRole role) {
        assertThatThrownBy(() -> create.create(actor(role), new CommunityNumber(9), Optional.empty()))
                .isInstanceOf(CommunityForbidden.class);
        assertThat(communities.stored).isEmpty();
        assertThat(catalog.calls).isEmpty();
    }

    @Test
    void updateLocksRenumbersRenamesAndProjectsTheNewNumber() {
        Community community = existing(12);

        Community updated = update.update(administrator, community.id(), new CommunityNumber(40),
                CommunityName.of("Santa Ana"));

        assertThat(communities.lockCalls).containsExactly(java.util.List.of(community.id()));
        assertThat(updated.number()).isEqualTo(new CommunityNumber(40));
        assertThat(updated.name()).hasValueSatisfying(name -> assertThat(name.value()).isEqualTo("Santa Ana"));
        assertThat(catalog.calls).containsExactly("renumber:" + community.id().value() + ":40");
    }

    @Test
    void updateKeepingItsOwnNumberIsNotATakenNumber() {
        Community community = existing(12);

        Community updated = update.update(administrator, community.id(), new CommunityNumber(12),
                CommunityName.of("Renamed"));

        assertThat(updated.name()).isPresent();
    }

    @Test
    void updateToTheNumberOfAnotherCommunityFailsOnTheNumberFieldAndChangesNothing() {
        Community community = existing(12);
        existing(13);

        assertNumberTaken(() -> update.update(administrator, community.id(), new CommunityNumber(13),
                Optional.empty()));
        assertThat(community.number()).isEqualTo(new CommunityNumber(12));
        assertThat(catalog.calls).isEmpty();
    }

    @Test
    void updateOfAnUnknownCommunityIsNotFound() {
        assertThatThrownBy(() -> update.update(administrator, new CommunityId(UUID.randomUUID()),
                new CommunityNumber(1), Optional.empty())).isInstanceOf(CommunityNotFound.class);
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void updateIsForbiddenForNonAdministrators(ActorRole role) {
        Community community = existing(12);

        assertThatThrownBy(() -> update.update(actor(role), community.id(), new CommunityNumber(40),
                Optional.empty())).isInstanceOf(CommunityForbidden.class);
        assertThat(community.number()).isEqualTo(new CommunityNumber(12));
    }

    @Test
    void deactivateAsksForTheUsersAndDeactivatesAnEmptyOfPrivilegedCommunity() {
        Community community = existing(12);
        users.users.add(new LinkedUser(new UserId(UUID.randomUUID()), ActorRole.REGISTERED, community.id(), "Test Person"));

        deactivate.deactivate(administrator, community.id(), false);

        assertThat(users.queried).containsExactly(community.id());
        assertThat(community.status()).isEqualTo(CommunityStatus.DEACTIVATED);
        assertThat(catalog.calls).containsExactly("setActive:" + community.id().value() + ":false");
    }

    @Test
    void deactivateWithPrivilegedUsersNeedsConfirmation() {
        Community community = existing(12);
        users.users.add(new LinkedUser(new UserId(UUID.randomUUID()), ActorRole.MODERATOR, community.id(), "Test Person"));

        assertThatThrownBy(() -> deactivate.deactivate(administrator, community.id(), false))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_HAS_PRIVILEGED_USERS"));
        assertThat(community.status()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(catalog.calls).isEmpty();
    }

    @Test
    void deactivateWithPrivilegedUsersAndConfirmationSucceeds() {
        Community community = existing(12);
        users.users.add(new LinkedUser(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, community.id(), "Test Person"));

        deactivate.deactivate(administrator, community.id(), true);

        assertThat(community.status()).isEqualTo(CommunityStatus.DEACTIVATED);
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void deactivateIsForbiddenForNonAdministrators(ActorRole role) {
        Community community = existing(12);

        assertThatThrownBy(() -> deactivate.deactivate(actor(role), community.id(), true))
                .isInstanceOf(CommunityForbidden.class);
        assertThat(community.status()).isEqualTo(CommunityStatus.ACTIVE);
    }

    @Test
    void deactivateOfAnUnknownCommunityIsNotFound() {
        assertThatThrownBy(() -> deactivate.deactivate(administrator, new CommunityId(UUID.randomUUID()), true))
                .isInstanceOf(CommunityNotFound.class);
    }

    @Test
    void reactivateRestoresTheCommunityAndItsProjection() {
        Community community = existing(12);
        community.deactivate(false, false);

        reactivate.reactivate(administrator, community.id());

        assertThat(community.status()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(communities.lockCalls).containsExactly(java.util.List.of(community.id()));
        assertThat(catalog.calls).containsExactly("setActive:" + community.id().value() + ":true");
    }

    @ParameterizedTest
    @EnumSource(value = ActorRole.class, names = {"REGISTERED", "MODERATOR"})
    void reactivateIsForbiddenForNonAdministrators(ActorRole role) {
        Community community = existing(12);
        community.deactivate(false, false);

        assertThatThrownBy(() -> reactivate.reactivate(actor(role), community.id()))
                .isInstanceOf(CommunityForbidden.class);
        assertThat(community.status()).isEqualTo(CommunityStatus.DEACTIVATED);
    }

    static Stream<Method> transactionalMethods() {
        return Stream.of(CreateCommunityService.class, UpdateCommunityService.class,
                DeactivateCommunityService.class, ReactivateCommunityService.class)
                .flatMap(type -> java.util.Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }

    @ParameterizedTest
    @MethodSource("transactionalMethods")
    void everyWriteUseCaseIsATransactionBoundary(Method method) {
        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void theTransactionalMethodsExist() {
        assertThat(transactionalMethods()).isNotEmpty();
    }
}
