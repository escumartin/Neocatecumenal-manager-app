package io.github.escumartin.neocatecumenal.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.escumartin.neocatecumenal.NeocatecumenalApplication;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.bootstrap.BootstrapAbortedException;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.bootstrap.BootstrapConsoleProvider;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.bootstrap.IdentityBootstrapRunner;
import io.github.escumartin.neocatecumenal.identity.infrastructure.in.bootstrap.BootstrapTermination;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class BootstrapRunnerIT {

    private static final Pattern PASSWORD = Pattern.compile("Contraseña inicial: (\\S+)");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
    private final AtomicInteger terminations = new AtomicInteger();
    private PrintStream originalOut;
    private Logger rootLogger;

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("drop schema public cascade");
            statement.execute("create schema public");
        }
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        originalOut = System.out;
        System.setOut(new PrintStream(standardOutput, true));
        appender.start();
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long count(String table) throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("select count(*) from " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static long scalar(String sql) throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private ConfigurableApplicationContext start(BootstrapConsoleProvider console, boolean noWeb, String... extraArgs) {
        SpringApplication application = new SpringApplication(NeocatecumenalApplication.class);
        if (noWeb) {
            application.setWebApplicationType(WebApplicationType.NONE);
        }
        application.addInitializers(context -> {
            context.getBeanFactory().registerSingleton("testConsole", console);
            context.getBeanFactory().registerSingleton("testTermination",
                    (BootstrapTermination) terminations::incrementAndGet);
        });
        application.addListeners((ApplicationListener<ApplicationPreparedEvent>) event ->
                rootLogger.addAppender(appender));
        Map<String, String> datasource = Map.of(
                "url", POSTGRES.getJdbcUrl(),
                "username", POSTGRES.getUsername(),
                "password", POSTGRES.getPassword());
        List<String> args = new ArrayList<>(List.of("--spring.main.banner-mode=off"));
        datasource.forEach((key, value) -> args.add("--spring.datasource.%s=%s".formatted(key, value)));
        args.addAll(List.of(extraArgs));
        return application.run(args.toArray(String[]::new));
    }

    private static String[] bootstrapArgs() {
        return new String[] {
            "--identity.bootstrap.enabled=true",
            "--identity.bootstrap.administrator-name=Test Admin",
            "--identity.bootstrap.administrator-community=2"
        };
    }

    private static BootstrapConsoleProvider consoleInto(StringWriter output) {
        return () -> Optional.of(new PrintWriter(output, true));
    }

    private String logsAndStandardOutput() {
        StringBuilder text = new StringBuilder(standardOutput.toString());
        for (ILoggingEvent event : new ArrayList<>(appender.list)) {
            text.append('\n').append(event.getFormattedMessage()).append(' ').append(event.getKeyValuePairs());
        }
        return text.toString();
    }

    private static Throwable rootOfType(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return cause;
            }
        }
        throw new AssertionError("no cause of type " + type.getSimpleName(), error);
    }

    @Test
    void withoutConsoleItAbortsWithANonZeroExitCodeAndCreatesNothing() throws SQLException {
        Throwable failure = catchThrowable(
                () -> start(Optional::empty, true, bootstrapArgs()).close());

        assertThat(failure).isNotNull();
        Throwable aborted = rootOfType(failure, BootstrapAbortedException.class);
        assertThat(((ExitCodeGenerator) aborted).getExitCode()).isNotZero();
        assertThat(count("identity_community")).isZero();
        assertThat(count("identity_user")).isZero();
        assertThat(terminations.get()).isZero();
        assertThat(logsAndStandardOutput())
                .contains("Carga inicial cancelada: hace falta una consola interactiva")
                .doesNotContain("Contraseña inicial")
                .doesNotContain("Identificador de acceso");
    }

    @Test
    void withAConsoleTheCredentialGoesOnlyThroughIt() throws SQLException {
        StringWriter console = new StringWriter();

        start(consoleInto(console), true, bootstrapArgs()).close();

        Matcher password = PASSWORD.matcher(console.toString());
        assertThat(password.find()).isTrue();
        long accessId = scalar("select access_id from identity_user");
        assertThat(console.toString()).contains(String.valueOf(accessId));
        assertThat(scalar("select count(*) from identity_user where role = 'ADMINISTRATOR'")).isEqualTo(1);
        assertThat(count("identity_community")).isEqualTo(1);
        assertThat(terminations.get()).isEqualTo(1);
        String elsewhere = logsAndStandardOutput();
        assertThat(elsewhere).doesNotContain(password.group(1)).doesNotContain(String.valueOf(accessId))
                .doesNotContain("Test Admin");
    }

    @Test
    void repeatingItAbortsWithANonZeroExitCodeWithoutWritingAnything() throws SQLException {
        start(consoleInto(new StringWriter()), true, bootstrapArgs()).close();
        long usersBefore = count("identity_user");
        long communitiesBefore = count("identity_community");
        terminations.set(0);
        StringWriter secondConsole = new StringWriter();

        Throwable failure = catchThrowable(() -> start(consoleInto(secondConsole), true,
                "--identity.bootstrap.enabled=true",
                "--identity.bootstrap.administrator-name=Other Admin",
                "--identity.bootstrap.administrator-community=9").close());

        Throwable aborted = rootOfType(failure, BootstrapAbortedException.class);
        assertThat(((ExitCodeGenerator) aborted).getExitCode()).isNotZero();
        assertThat(count("identity_user")).isEqualTo(usersBefore);
        assertThat(count("identity_community")).isEqualTo(communitiesBefore);
        assertThat(terminations.get()).isZero();
        assertThat(secondConsole.toString()).isEmpty();
        assertThat(logsAndStandardOutput()).contains("ya existe un administrador");
    }

    @Test
    void incompleteConfigurationAbortsWithoutCreatingAnything() throws SQLException {
        StringWriter console = new StringWriter();

        Throwable failure = catchThrowable(() -> start(consoleInto(console), true,
                "--identity.bootstrap.enabled=true").close());

        assertThat(rootOfType(failure, BootstrapAbortedException.class)).isNotNull();
        assertThat(count("identity_community")).isZero();
        assertThat(count("identity_user")).isZero();
        assertThat(console.toString()).isEmpty();
    }

    @Test
    void missingCommunityAbortsWithoutCreatingAnything() throws SQLException {
        Throwable failure = catchThrowable(() -> start(consoleInto(new StringWriter()), true,
                "--identity.bootstrap.enabled=true",
                "--identity.bootstrap.administrator-name=Test Admin").close());

        assertThat(rootOfType(failure, BootstrapAbortedException.class)).isNotNull();
        assertThat(count("identity_community")).isZero();
        assertThat(count("identity_user")).isZero();
    }

    @Test
    void passwordNeverReachesTheLogs() throws SQLException {
        StringWriter console = new StringWriter();

        start(consoleInto(console), true, bootstrapArgs()).close();

        Matcher password = PASSWORD.matcher(console.toString());
        assertThat(password.find()).isTrue();
        assertThat(logsAndStandardOutput()).doesNotContain(password.group(1));
    }

    @Test
    void waitsForTheBootstrapAdvisoryLockBeforeCheckingForAnAdministrator() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection holder = connection()) {
            holder.setAutoCommit(false);
            try (Statement statement = holder.createStatement()) {
                statement.execute("select pg_advisory_xact_lock(hashtext('identity-bootstrap'))");
            }
            Future<Throwable> run = executor.submit(() -> catchThrowable(
                    () -> start(consoleInto(new StringWriter()), true, bootstrapArgs()).close()));

            awaitBlockedOnAdvisoryLock();
            assertThat(run.isDone()).isFalse();
            assertThat(count("identity_user")).isZero();

            holder.commit();
            assertThat(run.get(60, TimeUnit.SECONDS)).isNull();
        } finally {
            executor.shutdownNow();
        }

        assertThat(scalar("select count(*) from identity_user where role = 'ADMINISTRATOR'")).isEqualTo(1);
    }

    private static void awaitBlockedOnAdvisoryLock() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (System.nanoTime() < deadline) {
            if (scalar("select count(*) from pg_stat_activity where wait_event_type = 'Lock'"
                    + " and wait_event = 'advisory'") > 0) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("la carga inicial no esperó al bloqueo consultivo");
    }

    @Test
    void bootstrapProfileStartsWithoutAWebServer() {
        String[] args = {
            "--spring.profiles.active=bootstrap",
            "--identity.bootstrap.administrator-name=Test Admin",
            "--identity.bootstrap.administrator-community=1"
        };

        try (ConfigurableApplicationContext context = start(consoleInto(new StringWriter()), false, args)) {
            assertThat(context).isNotInstanceOf(WebApplicationContext.class);
        }
    }

    @Test
    void disabledByDefaultItDoesNotTouchTheDatabaseNorNeedAConsole() throws SQLException {
        try (ConfigurableApplicationContext context = start(Optional::empty, true)) {
            assertThat(context.getBeansOfType(IdentityBootstrapRunner.class)).isEmpty();
        }
        assertThat(count("identity_user")).isZero();
    }
}
