package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryErasures.Entry;
import io.github.escumartin.neocatecumenal.identity.application.service.DeleteUserService;
import io.github.escumartin.neocatecumenal.identity.application.service.IdentityApiService;
import io.github.escumartin.neocatecumenal.identity.application.service.UpdateUserService;
import io.github.escumartin.neocatecumenal.identity.domain.User;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import org.junit.jupiter.api.Test;

class ErasureRecordingTest {

    private final UserManagementFixture fixture = new UserManagementFixture();

    private DeleteUserService deleteService() {
        return new DeleteUserService(fixture.currentSession, fixture.users, fixture.sessions, fixture.events,
                fixture.privacyNotice, fixture.guard, fixture.erasures, fixture.clock);
    }

    private IdentityApi api() {
        return new IdentityApiService(fixture.currentSession, fixture.users, fixture.communities,
                fixture.privacyNotice,
                new UpdateUserService(fixture.currentSession, fixture.users, fixture.communities,
                        fixture.privacyNotice, fixture.guard),
                deleteService());
    }

    @Test
    void deletingAUserRecordsTheErasureWithTheClockInstant() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        deleteService().delete(target.id());

        assertThat(fixture.erasures.entries).containsExactly(new Entry(target.id(), fixture.now));
    }

    @Test
    void deletingAUserThroughTheModuleApiRecordsTheErasure() {
        fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));
        User target = fixture.add(UserRole.REGISTERED, fixture.communityA);

        api().deleteUser(target.id().value());

        assertThat(fixture.erasures.entries).extracting(Entry::userId).containsExactly(target.id());
    }

    @Test
    void aRejectedDeletionRecordsNothing() {
        User moderator = fixture.actAs(fixture.add(UserRole.MODERATOR, fixture.communityA));

        assertThatThrownBy(() -> deleteService().delete(moderator.id())).isNotNull();

        assertThat(fixture.erasures.entries).isEmpty();
    }
}
