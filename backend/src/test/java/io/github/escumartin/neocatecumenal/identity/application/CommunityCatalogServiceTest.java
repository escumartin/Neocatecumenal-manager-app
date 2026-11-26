package io.github.escumartin.neocatecumenal.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.identity.IdentityRuleViolation;
import io.github.escumartin.neocatecumenal.identity.TestUsers;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.FakeHasher;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryCommunities;
import io.github.escumartin.neocatecumenal.identity.application.Fakes.InMemoryUsers;
import io.github.escumartin.neocatecumenal.identity.application.service.CommunityCatalogService;
import io.github.escumartin.neocatecumenal.identity.domain.AccessId;
import io.github.escumartin.neocatecumenal.identity.domain.Community;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityId;
import io.github.escumartin.neocatecumenal.identity.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.identity.domain.DisplayName;
import io.github.escumartin.neocatecumenal.identity.domain.PlainPassword;
import io.github.escumartin.neocatecumenal.identity.domain.PrivacyConsent;
import io.github.escumartin.neocatecumenal.identity.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommunityCatalogServiceTest {

    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryCommunities communities = new InMemoryCommunities(users);
    private final CommunityCatalogService catalog = new CommunityCatalogService(communities);

    @Test
    void registerInsertsAnActiveProjection() {
        UUID id = UUID.randomUUID();

        catalog.register(id, 12);

        assertThat(communities.findById(new CommunityId(id))).contains(
                new Community(new CommunityId(id), new CommunityNumber(12), true));
    }

    @Test
    void renumberChangesOnlyTheNumber() {
        UUID id = UUID.randomUUID();
        catalog.register(id, 12);

        catalog.renumber(id, 30);

        assertThat(communities.findById(new CommunityId(id))).contains(
                new Community(new CommunityId(id), new CommunityNumber(30), true));
    }

    @Test
    void setActiveDeactivatesAndReactivates() {
        UUID id = UUID.randomUUID();
        catalog.register(id, 12);

        catalog.setActive(id, false);
        assertThat(communities.findById(new CommunityId(id)).orElseThrow().active()).isFalse();

        catalog.setActive(id, true);
        assertThat(communities.findById(new CommunityId(id)).orElseThrow().active()).isTrue();
    }

    @Test
    void lockForRemovalTakesTheExclusiveLockOnTheProjection() {
        UUID id = UUID.randomUUID();
        catalog.register(id, 12);

        catalog.lockForRemoval(id);

        assertThat(users.calls).contains("communityLockForUpdate");
    }

    @Test
    void removeDeletesAnEmptyCommunity() {
        UUID id = UUID.randomUUID();
        catalog.register(id, 12);

        catalog.remove(id);

        assertThat(communities.findById(new CommunityId(id))).isEmpty();
    }

    @Test
    void removeIsRejectedWhileThereAreUsers() {
        UUID id = UUID.randomUUID();
        catalog.register(id, 12);
        users.save(TestUsers.newUser(new UserId(UUID.randomUUID()), new AccessId(123456),
                new DisplayName("Test Person"), UserRole.REGISTERED, new CommunityId(id),
                new FakeHasher().hash(new PlainPassword("irrelevant-pass")), false,
                new PrivacyConsent("v2", Instant.parse("2026-03-01T10:00:00Z"))));

        assertThatThrownBy(() -> catalog.remove(id))
                .isInstanceOfSatisfying(IdentityRuleViolation.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_HAS_USERS"));
        assertThat(communities.findById(new CommunityId(id))).isPresent();
    }
}
