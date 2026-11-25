package io.github.escumartin.neocatecumenal.community;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.community.application.port.in.CreateCommunity;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import io.github.escumartin.neocatecumenal.identity.CommunityProvisioner;
import io.github.escumartin.neocatecumenal.identity.CommunityRef;
import io.github.escumartin.neocatecumenal.identity.IdentityApi;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@Testcontainers
class CommunityModuleTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private CreateCommunity createCommunity;

    @Autowired
    private CommunityProvisioner provisioner;

    @Autowired
    private IdentityApi identity;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate community_contact_consent, community_member, community, identity_session, "
                + "identity_user, identity_community cascade");
    }

    @Test
    void identityListsACommunityCreatedByTheCommunityModule() {
        Actor administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR,
                new CommunityId(UUID.randomUUID()));

        Community created = createCommunity.create(administrator, new CommunityNumber(12), Optional.empty());

        assertThat(identity.communities()).contains(new CommunityRef(created.id().value(), 12, true));
    }

    @Test
    void theInitialLoadCreatesTheCommunityInBothTablesWithTheSameId() {
        UUID id = provisioner.ensure(7);

        assertThat(jdbc.queryForObject("select number from community where id = ?", Integer.class, id))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject("select number from identity_community where id = ?", Integer.class, id))
                .isEqualTo(7);
    }

    @Test
    void theInitialLoadIsIdempotent() {
        UUID first = provisioner.ensure(7);

        assertThat(provisioner.ensure(7)).isEqualTo(first);
        assertThat(jdbc.queryForObject("select count(*) from community", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from identity_community", Integer.class)).isEqualTo(1);
    }
}
