package io.github.escumartin.neocatecumenal.community.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

final class RaceHarness {

    private static final String LOCK_COMMUNITY = "select id from community where id = ? for update";
    private static final String LOCK_USER = "select id from identity_user where id = ? for update";
    private static final long TIMEOUT_SECONDS = 30;

    record Response(int status, String body) {

        String code() {
            for (String path : List.of("$.errors[0].code", "$.code")) {
                try {
                    return JsonPath.read(body, path);
                } catch (RuntimeException absent) {
                    continue;
                }
            }
            return "";
        }
    }

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private long deadlocksBefore;

    RaceHarness(MockMvc mockMvc, JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    void startWatchingDeadlocks() {
        deadlocksBefore = deadlocks();
    }

    void assertNoDeadlocksSinceStart() throws InterruptedException {
        Thread.sleep(1500);
        assertThat(deadlocks()).as("deadlocks detected by PostgreSQL (40P01)").isEqualTo(deadlocksBefore);
    }

    Response send(MockHttpServletRequestBuilder request) throws Exception {
        var response = mockMvc.perform(request).andReturn().getResponse();
        return new Response(response.getStatus(), response.getContentAsString());
    }

    List<Response> simultaneously(List<Callable<Response>> actions) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(actions.size());
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        try {
            List<Future<Response>> futures = new ArrayList<>();
            for (Callable<Response> action : actions) {
                futures.add(executor.submit(() -> {
                    barrier.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return action.call();
                }));
            }
            return collect(futures);
        } finally {
            executor.shutdownNow();
        }
    }

    List<Response> queuedBehindCommunity(UUID community, List<Callable<Response>> arrivalOrder) throws Exception {
        return queuedBehind(LOCK_COMMUNITY, community, jdbc -> { }, arrivalOrder);
    }

    List<Response> queuedBehindUser(UUID user, Consumer<JdbcTemplate> whileHeld,
            List<Callable<Response>> arrivalOrder) throws Exception {
        return queuedBehind(LOCK_USER, user, whileHeld, arrivalOrder);
    }

    List<Response> queuedBehindCommunity(UUID community, Consumer<JdbcTemplate> whileHeld,
            List<Callable<Response>> arrivalOrder) throws Exception {
        return queuedBehind(LOCK_COMMUNITY, community, whileHeld, arrivalOrder);
    }

    private List<Response> queuedBehind(String lockSql, UUID id, Consumer<JdbcTemplate> whileHeld,
            List<Callable<Response>> arrivalOrder) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(arrivalOrder.size());
        List<Future<Response>> futures = new ArrayList<>();
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.queryForList(lockSql, id);
                for (Callable<Response> action : arrivalOrder) {
                    futures.add(executor.submit(action));
                    awaitWaiters(futures.size());
                }
                whileHeld.accept(jdbc);
            });
            return collect(futures);
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitWaiters(int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where "
                    + "wait_event_type = 'Lock' and datname = current_database()", Integer.class);
            if (waiting != null && waiting >= expected) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
        throw new AssertionError("expected " + expected + " backends waiting for a lock");
    }

    private static List<Response> collect(List<Future<Response>> futures) throws Exception {
        List<Response> responses = new ArrayList<>();
        for (Future<Response> future : futures) {
            try {
                responses.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            } catch (ExecutionException failure) {
                throw failure.getCause() instanceof Exception cause ? cause : failure;
            }
        }
        return responses;
    }

    private long deadlocks() {
        Long count = jdbc.queryForObject("select deadlocks from pg_stat_database where datname = "
                + "current_database()", Long.class);
        return count == null ? 0 : count;
    }
}
