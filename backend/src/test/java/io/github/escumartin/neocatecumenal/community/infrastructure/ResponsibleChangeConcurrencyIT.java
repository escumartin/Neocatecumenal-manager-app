package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.escumartin.neocatecumenal.community.application.port.in.DesignateResponsible;
import io.github.escumartin.neocatecumenal.community.application.port.in.GetCommunity;
import io.github.escumartin.neocatecumenal.community.application.port.out.CommunityRepository;
import io.github.escumartin.neocatecumenal.community.application.port.out.MemberRepository;
import io.github.escumartin.neocatecumenal.community.domain.Actor;
import io.github.escumartin.neocatecumenal.community.domain.ActorRole;
import io.github.escumartin.neocatecumenal.community.domain.Community;
import io.github.escumartin.neocatecumenal.community.domain.CommunityId;
import io.github.escumartin.neocatecumenal.community.domain.CommunityNumber;
import io.github.escumartin.neocatecumenal.community.domain.Member;
import io.github.escumartin.neocatecumenal.community.domain.MemberId;
import io.github.escumartin.neocatecumenal.community.domain.PersonName;
import io.github.escumartin.neocatecumenal.community.domain.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class ResponsibleChangeConcurrencyIT {

    private static final int ROUNDS = 3;
    private static final int CHANGES_PER_WRITER = 25;
    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private CommunityRepository communities;

    @Autowired
    private MemberRepository members;

    @Autowired
    private DesignateResponsible designateResponsible;

    @Autowired
    private GetCommunity getCommunity;

    @Autowired
    private JdbcTemplate jdbc;

    private Community community;
    private List<Member> candidates;
    private Actor administrator;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate community_contact_consent, community_member, community, identity_session, "
                + "identity_user, identity_community cascade");
        community = communities.save(Community.create(new CommunityId(UUID.randomUUID()), new CommunityNumber(1),
                Optional.empty()));
        candidates = new ArrayList<>();
        for (String given : List.of("Ana", "Berta", "Carlos", "Diego")) {
            candidates.add(members.save(Member.register(new MemberId(UUID.randomUUID()), community,
                    PersonName.of(given, null), Optional.empty(), Optional.empty(), NOW)));
        }
        administrator = new Actor(new UserId(UUID.randomUUID()), ActorRole.ADMINISTRATOR, community.id());
    }

    @RepeatedTest(ROUNDS)
    void noReaderSeesTheCommunityWithoutResponsibleOnceDesignatedWhileTwoWritersReplaceIt() throws Exception {
        designateResponsible.designate(administrator, community.id(), candidates.get(0).id());
        AtomicBoolean writing = new AtomicBoolean(true);
        AtomicInteger readsWithoutResponsible = new AtomicInteger();
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(4);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> writers = new ArrayList<>();
            for (int writer = 0; writer < 2; writer++) {
                int offset = writer;
                writers.add(executor.submit(() -> {
                    ready.countDown();
                    ready.await();
                    for (int change = 0; change < CHANGES_PER_WRITER; change++) {
                        Member next = candidates.get((change + offset) % candidates.size());
                        designateResponsible.designate(administrator, community.id(), next.id());
                    }
                    return null;
                }));
            }
            List<Future<?>> readers = new ArrayList<>();
            for (int reader = 0; reader < 2; reader++) {
                int useUseCase = reader;
                readers.add(executor.submit(() -> {
                    ready.countDown();
                    ready.await();
                    while (writing.get()) {
                        boolean withResponsible = useUseCase == 0
                                ? getCommunity.get(administrator, community.id()).community().hasResponsible()
                                : jdbc.queryForObject("select responsible_member_id is not null from community "
                                        + "where id = ?", Boolean.class, community.id().value());
                        reads.incrementAndGet();
                        if (!withResponsible) {
                            readsWithoutResponsible.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> writer : writers) {
                writer.get();
            }
            writing.set(false);
            for (Future<?> reader : readers) {
                reader.get();
            }

            assertThat(reads.get()).isPositive();
            assertThat(readsWithoutResponsible.get()).isZero();
            assertThat(communities.findById(community.id()))
                    .hasValueSatisfying(stored -> assertThat(stored.hasResponsible()).isTrue());
        } finally {
            writing.set(false);
            executor.shutdownNow();
        }
    }

    @RepeatedTest(ROUNDS)
    void sequencesOfDesignationsAlwaysLeaveTheLastOneAsTheResponsible() {
        for (Member candidate : candidates) {
            designateResponsible.designate(administrator, community.id(), candidate.id());

            assertThat(communities.findById(community.id())).hasValueSatisfying(
                    stored -> assertThat(stored.isResponsible(candidate.id())).isTrue());
        }
    }
}
