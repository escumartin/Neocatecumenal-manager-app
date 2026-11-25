package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityStatus;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionRepository;
import io.github.escumartin.neocatecumenal.identity.application.port.out.SessionTokenIssuer;
import io.github.escumartin.neocatecumenal.identity.application.port.out.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class LockRereadIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private CommunityRepository communities;

    @Autowired
    private MemberRepository members;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private SessionTokenIssuer tokens;

    private UUID home;
    private UUID other;
    private UUID member;

    @BeforeEach
    void setUp() {
        CommunityApiFixture fixture = new CommunityApiFixture(jdbc, users, sessions, tokens);
        fixture.reset();
        home = fixture.community(1);
        other = fixture.community(2);
        member = fixture.member(home, "Ana");
    }

    @Test
    void lockingAMemberReadEarlierInTheTransactionReturnsTheCommittedState() {
        Member lockedMember = transaction.execute(status -> {
            members.findById(new MemberId(member));
            jdbc.update("update community_member set community_id = ?, version = version + 1 where id = ?", other,
                    member);
            return members.lock(new MemberId(member)).orElseThrow();
        });

        assertThat(lockedMember.communityId()).isEqualTo(new CommunityId(other));
    }

    @Test
    void lockingACommunityReadEarlierInTheTransactionReturnsTheCommittedState() {
        Community locked = transaction.execute(status -> {
            communities.findById(new CommunityId(home));
            jdbc.update("update community set status = 'DEACTIVATED', version = version + 1 where id = ?", home);
            return communities.lockInIdOrder(new CommunityId(home)).getFirst();
        });

        assertThat(locked.status()).isEqualTo(CommunityStatus.DEACTIVATED);
    }
}
