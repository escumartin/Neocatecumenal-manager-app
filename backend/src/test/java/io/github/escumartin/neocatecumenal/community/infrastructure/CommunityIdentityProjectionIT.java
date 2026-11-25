package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.port.in.CreateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.DeactivateCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.in.DeleteCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.IdentityCommunityCatalog;
import io.github.escumartin.neocatecumenal.community.application.port.out.UserDirectory;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.LinkedUser;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.CommunityRef;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import io.github.escumartin.neocatecumenal.identity.domain.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class CommunityIdentityProjectionIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private IdentityCommunityCatalog catalog;

    @Autowired
    private UserDirectory userDirectory;

    @Autowired
    private CommunityRepository communities;

    @Autowired
    private CreateCommunity createCommunity;

    @Autowired
    private DeactivateCommunity deactivateCommunity;

    @Autowired
    private DeleteCommunity deleteCommunity;

    @Autowired
    private IdentityApi identity;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserRepository users;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private SessionTokenIssuer tokens;

    private CommunityApiFixture fixture;
    private Actor administrator;

    @BeforeEach
    void setUp() {
        fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR,
                new CommunityId(UUID.randomUUID()));
    }

    private Optional<CommunityRef> projectionOf(UUID id) {
        return identity.communities().stream().filter(community -> community.id().equals(id)).findFirst();
    }

    @Test
    void registerWritesAnActiveProjection() {
        CommunityId id = new CommunityId(UUID.randomUUID());

        catalog.register(id, new CommunityNumber(12));

        assertThat(projectionOf(id.value())).hasValue(new CommunityRef(id.value(), 12, true));
    }

    @Test
    void renumberChangesTheProjectedNumber() {
        CommunityId id = new CommunityId(UUID.randomUUID());
        catalog.register(id, new CommunityNumber(12));

        catalog.renumber(id, new CommunityNumber(30));

        assertThat(projectionOf(id.value())).hasValue(new CommunityRef(id.value(), 30, true));
    }

    @Test
    void setActiveTogglesTheProjectedState() {
        CommunityId id = new CommunityId(UUID.randomUUID());
        catalog.register(id, new CommunityNumber(12));

        catalog.setActive(id, false);
        assertThat(projectionOf(id.value())).hasValue(new CommunityRef(id.value(), 12, false));

        catalog.setActive(id, true);
        assertThat(projectionOf(id.value())).hasValue(new CommunityRef(id.value(), 12, true));
    }

    @Test
    void removeDeletesTheProjection() {
        CommunityId id = new CommunityId(UUID.randomUUID());
        catalog.register(id, new CommunityNumber(12));

        catalog.remove(id);

        assertThat(projectionOf(id.value())).isEmpty();
    }

    @Test
    void removeWithUsersIsTranslatedToCommunityNotEmptyByTheForeignKey() {
        UUID id = fixture.community(12);
        fixture.user(UserRole.REGISTERED, id);

        assertThatThrownBy(() -> catalog.remove(new CommunityId(id)))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_NOT_EMPTY"));
        assertThat(projectionOf(id)).isPresent();
    }

    @Test
    void userDirectoryListsTheUsersOfACommunityWithTheirRoles() {
        UUID id = fixture.community(12);
        UUID moderator = fixture.user(UserRole.MODERATOR, id);
        fixture.user(UserRole.REGISTERED, fixture.community(13));

        List<LinkedUser> found = userDirectory.usersOf(new CommunityId(id));

        assertThat(found).containsExactly(new LinkedUser(new UserId(moderator), ActorRole.MODERATOR,
                new CommunityId(id), "Test Person"));
    }

    @Test
    void creatingACommunityWritesBothTablesInTheSameTransaction() {
        Community created = createCommunity.create(administrator, new CommunityNumber(12), Optional.empty());

        assertThat(communities.findById(created.id())).isPresent();
        assertThat(projectionOf(created.id().value())).isPresent();
    }

    @Test
    void aFailingProjectionRollsBackTheCommunityWrite() {
        jdbc.update("insert into identity_community (id, number, active) values (?, 12, true)", UUID.randomUUID());

        assertThatThrownBy(() -> createCommunity.create(administrator, new CommunityNumber(12), Optional.empty()))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("select count(*) from community where number = 12", Integer.class)).isZero();
    }

    @Test
    void aRollbackAfterAWriteLeavesNeitherTable() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            createCommunity.create(administrator, new CommunityNumber(12), Optional.empty());
            status.setRollbackOnly();
        });

        assertThat(jdbc.queryForObject("select count(*) from community", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from identity_community", Integer.class)).isZero();
    }

    @Test
    void deletingACommunityWithUsersAndNoMembersIsRejectedAsNotEmpty() {
        UUID id = fixture.community(12);
        fixture.user(UserRole.REGISTERED, id);

        assertThatThrownBy(() -> deleteCommunity.delete(administrator, new CommunityId(id)))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_NOT_EMPTY"));
        assertThat(projectionOf(id)).isPresent();
    }

    @Test
    void deletingACommunityWithBookingRequestsIsRejectedAsHavingHistoryAndItCanStillBeDeactivated() {
        UUID id = fixture.community(12);
        UUID room = UUID.randomUUID();
        jdbc.update("insert into room (id, name, active, version, created_at) values (?, 'Main hall', true, 0, now())",
                room);
        jdbc.update("insert into booking_request (id, room_id, community_id, reason, first_date, last_date, "
                + "start_time, end_time, status, version, created_at) values (?, ?, ?, 'Meeting', '2030-05-10', "
                + "'2030-05-10', '18:00', '19:00', 'PENDING', 0, now())", UUID.randomUUID(), room, id);

        assertThatThrownBy(() -> deleteCommunity.delete(administrator, new CommunityId(id)))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_HAS_HISTORY"));
        assertThat(communities.findById(new CommunityId(id))).isPresent();

        deactivateCommunity.deactivate(administrator, new CommunityId(id), false);

        assertThat(projectionOf(id)).hasValueSatisfying(community -> assertThat(community.active()).isFalse());
    }

    @Test
    void deletingACommunityWithEventsIsRejectedAsHavingHistory() {
        UUID id = fixture.community(12);
        jdbc.update("insert into event (id, community_id, type, title, event_date, start_time, end_time, room_id, "
                + "booking_request_id, created_at) values (?, ?, 'WORD', 'Opening word', '2030-05-10', '18:00', "
                + "'19:00', ?, ?, now())", UUID.randomUUID(), id, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> deleteCommunity.delete(administrator, new CommunityId(id)))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_HAS_HISTORY"));
    }

    @Test
    void deletingAnEmptyCommunityRemovesTheRowAndTheProjection() {
        UUID id = fixture.community(12);

        deleteCommunity.delete(administrator, new CommunityId(id));

        assertThat(communities.findById(new CommunityId(id))).isEmpty();
        assertThat(projectionOf(id)).isEmpty();
    }
}
