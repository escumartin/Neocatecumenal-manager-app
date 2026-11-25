package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityDomainException;
import io.github.escumartin.neocatecumenal.community.domain.CommunityFieldInvalid;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityName;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.CommunityStatus;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
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
class CommunityPersistenceIT {

    private static final String LOCK_NOT_AVAILABLE = "55P03";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private CommunityRepository communities;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate community_contact_consent, community_member, community, identity_session, "
                + "identity_user, identity_community cascade");
    }

    private Community newCommunity(int number, String name) {
        return Community.create(new CommunityId(UUID.randomUUID()), new CommunityNumber(number),
                CommunityName.of(name));
    }

    private long versionOf(Community community) {
        return jdbc.queryForObject("select version from community where id = ?", Long.class,
                community.id().value());
    }

    @Test
    void savedCommunityRoundTrips() {
        Community saved = communities.save(newCommunity(12, "San Pedro"));

        Community loaded = communities.findById(saved.id()).orElseThrow();

        assertThat(loaded.number()).isEqualTo(new CommunityNumber(12));
        assertThat(loaded.name()).hasValueSatisfying(name -> assertThat(name.value()).isEqualTo("San Pedro"));
        assertThat(loaded.status()).isEqualTo(CommunityStatus.ACTIVE);
        assertThat(loaded.hasResponsible()).isFalse();
    }

    @Test
    void anAbsentNameIsStoredAsNull() {
        Community saved = communities.save(newCommunity(12, null));

        assertThat(jdbc.queryForObject("select name from community where id = ?", String.class,
                saved.id().value())).isNull();
        assertThat(communities.findById(saved.id()).orElseThrow().name()).isEmpty();
    }

    @Test
    void changesArePersistedAndTheVersionIsBumped() {
        Community saved = communities.save(newCommunity(12, "San Pedro"));
        long initial = versionOf(saved);

        saved.renumber(new CommunityNumber(40));
        saved.rename(Optional.empty());
        saved.deactivate(false, false);
        communities.save(saved);

        Community loaded = communities.findById(saved.id()).orElseThrow();
        assertThat(loaded.number()).isEqualTo(new CommunityNumber(40));
        assertThat(loaded.name()).isEmpty();
        assertThat(loaded.status()).isEqualTo(CommunityStatus.DEACTIVATED);
        assertThat(versionOf(saved)).isGreaterThan(initial);
    }

    @Test
    void findByNumberFindsTheCommunityOrNothing() {
        Community saved = communities.save(newCommunity(12, null));

        assertThat(communities.findByNumber(new CommunityNumber(12))).hasValueSatisfying(
                found -> assertThat(found.id()).isEqualTo(saved.id()));
        assertThat(communities.findByNumber(new CommunityNumber(13))).isEmpty();
    }

    @Test
    void findAllIsOrderedByNumber() {
        communities.save(newCommunity(30, null));
        communities.save(newCommunity(2, null));
        communities.save(newCommunity(11, null));

        assertThat(communities.findAll()).extracting(community -> community.number().value())
                .containsExactly(2, 11, 30);
    }

    @Test
    void aDuplicatedNumberIsTranslatedToCommunityNumberTaken() {
        communities.save(newCommunity(12, null));

        assertThatThrownBy(() -> communities.save(newCommunity(12, "Other")))
                .isInstanceOfSatisfying(CommunityFieldInvalid.class, e -> {
                    assertThat(e.field()).isEqualTo("number");
                    assertThat(e.code()).isEqualTo("COMMUNITY_NUMBER_TAKEN");
                });
        assertThat(jdbc.queryForObject("select count(*) from community", Integer.class)).isEqualTo(1);
    }

    @Test
    void renumberingOntoAnExistingNumberIsTranslatedToCommunityNumberTaken() {
        communities.save(newCommunity(12, null));
        Community other = communities.save(newCommunity(13, null));
        other.renumber(new CommunityNumber(12));

        assertThatThrownBy(() -> communities.save(other)).isInstanceOfSatisfying(CommunityFieldInvalid.class,
                e -> assertThat(e.code()).isEqualTo("COMMUNITY_NUMBER_TAKEN"));
    }

    @Test
    void deleteRemovesTheRow() {
        Community saved = communities.save(newCommunity(12, null));

        communities.delete(saved.id());

        assertThat(communities.findById(saved.id())).isEmpty();
    }

    @Test
    void deleteOfACommunityWithMembersIsTranslatedToCommunityNotEmpty() {
        Community saved = communities.save(newCommunity(12, null));
        jdbc.update("insert into community_member (id, community_id, given_name, version) values (?, ?, 'Test', 0)",
                UUID.randomUUID(), saved.id().value());

        assertThatThrownBy(() -> communities.delete(saved.id()))
                .isInstanceOfSatisfying(CommunityDomainException.class,
                        e -> assertThat(e.code()).isEqualTo("COMMUNITY_NOT_EMPTY"));
        assertThat(communities.findById(saved.id())).isPresent();
    }

    @Test
    void lockInIdOrderReturnsTheCommunitiesAndIgnoresUnknownIds() {
        Community first = communities.save(newCommunity(1, null));
        Community second = communities.save(newCommunity(2, null));
        CommunityId unknown = new CommunityId(UUID.randomUUID());

        List<Community> locked = new TransactionTemplate(transactionManager).execute(
                status -> communities.lockInIdOrder(first.id(), unknown, second.id()));

        assertThat(locked).extracting(Community::id).containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void lockInIdOrderTakesTheLocksInAscendingIdOrderRegardlessOfTheArgumentOrder() throws Exception {
        communities.save(newCommunity(1, null));
        communities.save(newCommunity(2, null));
        List<UUID> ascending = jdbc.queryForList("select id from community order by id", UUID.class);
        UUID lower = ascending.get(0);
        UUID higher = ascending.get(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (var statement = holder.prepareStatement("select id from community where id = ? for update")) {
                statement.setObject(1, higher);
                statement.executeQuery();
            }

            Future<List<Community>> contender = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .execute(status -> communities.lockInIdOrder(new CommunityId(higher), new CommunityId(lower))));

            assertThat(lowerIsEventuallyLockedByTheContender(lower)).isTrue();
            assertThat(contender.isDone()).isFalse();

            holder.commit();
            List<Community> locked = contender.get(10, TimeUnit.SECONDS);
            assertThat(locked).extracting(community -> community.id().value()).containsExactly(lower, higher);
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean lowerIsEventuallyLockedByTheContender(UUID lower) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try (Connection probe = dataSource.getConnection();
                    var statement = probe.prepareStatement(
                            "select id from community where id = ? for update nowait")) {
                statement.setObject(1, lower);
                statement.executeQuery();
            } catch (SQLException failure) {
                if (LOCK_NOT_AVAILABLE.equals(failure.getSQLState())) {
                    return true;
                }
                throw failure;
            }
            Thread.sleep(50);
        }
        return false;
    }
}
