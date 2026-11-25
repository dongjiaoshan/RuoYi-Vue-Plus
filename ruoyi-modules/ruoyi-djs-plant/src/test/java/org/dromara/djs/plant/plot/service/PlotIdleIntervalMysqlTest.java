package org.dromara.djs.plant.plot.service;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.dromara.djs.plant.plot.mapper.PlotIdleIntervalMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Tag("local")
@Tag("dev")
@EnabledIfEnvironmentVariable(named = "DJS_STAT_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:(3306|3316)/.*")
class PlotIdleIntervalMysqlTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 10);
    private static final String TENANT = "1001";
    private static final String TABLE_DDL = "ruoyi-admin/src/main/resources/db/migration/"
        + "V202609301130__PLT-STAT-V11-plot-idle-interval.sql";

    private Connection fixture;
    private String schema;
    private SqlSessionFactory sessions;

    @BeforeEach
    void setup() throws Exception {
        fixture = connect();
        // MySQL TEMPORARY TABLE 只对创建它的连接可见。两个事务使用同一组独立测试表。
        String schemaName = "djs_idle_test_" + UUID.randomUUID().toString().replace("-", "");
        sql(fixture, "CREATE DATABASE " + schemaName);
        schema = schemaName;
        fixture.setCatalog(schema);
        sql(fixture, """
            CREATE TABLE t_plant_plot_info (
                id BIGINT PRIMARY KEY, plot_status INT NOT NULL DEFAULT 3,
                tenant_id VARCHAR(20) NOT NULL DEFAULT '1001', del_flag CHAR(1) NOT NULL DEFAULT '0'
            ) ENGINE=InnoDB
            """);
        Path repo = Path.of("").toAbsolutePath();
        while (repo != null && !Files.isRegularFile(repo.resolve(TABLE_DDL))) {
            repo = repo.getParent();
        }
        assertThat(repo).as("后端仓库根目录").isNotNull();
        for (String statement : Files.readString(repo.resolve(TABLE_DDL)).split(";")) {
            // 双连接测试只需区间表与地块同步列。历史回填由 ProductionIndicatorsMysqlTest 验证。
            if (statement.stripLeading().startsWith("CREATE TABLE")
                || statement.stripLeading().startsWith("ALTER TABLE t_plant_plot_info")) {
                sql(fixture, statement);
            }
        }
    }

    @AfterEach
    void cleanup() throws SQLException {
        if (fixture != null) {
            try (Connection connection = fixture) {
                if (schema != null) {
                    sql(connection, "DROP DATABASE " + schema);
                }
            }
        }
    }

    @Test
    void adjacentPlotsWithoutHistoryBothCommitAtRepeatableRead() throws Exception {
        sql(fixture, "INSERT INTO t_plant_plot_info (id) VALUES (10),(11)");
        sql(fixture, """
            INSERT INTO t_plant_plot_idle_interval (id, plot_id, idle_started_on, idle_ended_on, start_source)
            VALUES (1,9,'2026-09-01','2026-09-02','fixture'), (2,12,'2026-09-01','2026-09-02','fixture')
            """);
        sessions = mapperSessions(new PauseBeforeOpen(new CyclicBarrier(2)));

        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<Void> first = workers.submit(() -> { rotateAndCommit(10); return null; });
            Future<Void> second = workers.submit(() -> { rotateAndCommit(11); return null; });
            assertAll(
                () -> first.get(20, TimeUnit.SECONDS),
                () -> second.get(20, TimeUnit.SECONDS));
        }

        assertThat(openIntervals()).containsExactly("10:2026-09-10", "11:2026-09-10");
        assertThat(plotStatuses()).containsExactly(1, 1);
    }

    @Test
    void existingSnapshotStillUsesTheLatestCommittedEndDate() throws Exception {
        sql(fixture, "INSERT INTO t_plant_plot_info (id, plot_status) VALUES (10,1)");
        sessions = mapperSessions();
        try (SqlSession initial = sessions.openSession(repeatableReadConnection())) {
            service(initial).recordAfterPlotWrite(10L, DAY.minusDays(9), "plot_create");
            initial.commit(true);
        }
        assertThat(openIntervals()).containsExactly("10:2026-09-01");

        try (Connection rotateConnection = repeatableReadConnection();
             SqlSession rotate = sessions.openSession(rotateConnection);
             Connection startConnection = repeatableReadConnection();
             SqlSession start = sessions.openSession(startConnection)) {
            try (var statement = rotateConnection.createStatement();
                 var snapshot = statement.executeQuery("""
                     SELECT idle_ended_on FROM t_plant_plot_idle_interval WHERE plot_id=10
                     """)) {
                assertThat(snapshot.next()).isTrue();
                assertThat(snapshot.getDate(1)).isNull();
            }
            setStatus(startConnection, 10, 2);
            service(start).recordAfterPlotWrite(10L, DAY.plusDays(2), "plant_start");
            start.commit(true);

            setStatus(rotateConnection, 10, 1);
            service(rotate).recordAfterPlotWrite(10L, DAY, "rotation");
            rotate.commit(true);
        }

        assertThat(openIntervals()).containsExactly("10:2026-09-12");
    }

    private void rotateAndCommit(long plotId) throws SQLException {
        try (Connection connection = repeatableReadConnection();
             SqlSession session = sessions.openSession(connection)) {
            setStatus(connection, plotId, 1);
            service(session).recordAfterPlotWrite(plotId, DAY, "rotation");
            session.commit(true);
        }
    }

    private PlotIdleIntervalService service(SqlSession session) {
        return new PlotIdleIntervalService(session.getMapper(PlotIdleIntervalMapper.class));
    }

    private SqlSessionFactory mapperSessions(Interceptor... interceptors) {
        Configuration config = new Configuration(new Environment("local", new JdbcTransactionFactory(),
            new SingleConnectionDataSource(fixture, true)));
        for (Interceptor interceptor : interceptors) {
            config.addInterceptor(interceptor);
        }
        MybatisPlusInterceptor tenant = new MybatisPlusInterceptor();
        tenant.addInnerInterceptor(new TenantLineInnerInterceptor(() -> new StringValue(TENANT)));
        config.addInterceptor(tenant);
        config.addMapper(PlotIdleIntervalMapper.class);
        return new SqlSessionFactoryBuilder().build(config);
    }

    private Connection repeatableReadConnection() throws SQLException {
        Connection connection = connect();
        try {
            connection.setCatalog(schema);
            sql(connection, "SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ");
            sql(connection, "SET SESSION innodb_lock_wait_timeout = 10");
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("DJS_STAT_MYSQL_URL"),
            System.getenv("DJS_STAT_MYSQL_USER"), System.getenv("DJS_STAT_MYSQL_PASSWORD"));
    }

    private static void setStatus(Connection connection, long plotId, int status) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE t_plant_plot_info SET plot_status=? WHERE id=?")) {
            statement.setInt(1, status);
            statement.setLong(2, plotId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private List<String> openIntervals() throws SQLException {
        try (var statement = fixture.createStatement(); var rows = statement.executeQuery("""
            SELECT CONCAT(plot_id, ':', idle_started_on)
              FROM t_plant_plot_idle_interval WHERE idle_ended_on IS NULL ORDER BY plot_id
            """)) {
            var intervals = new ArrayList<String>();
            while (rows.next()) {
                intervals.add(rows.getString(1));
            }
            return intervals;
        }
    }

    private List<Integer> plotStatuses() throws SQLException {
        try (var statement = fixture.createStatement();
             var rows = statement.executeQuery("SELECT plot_status FROM t_plant_plot_info ORDER BY id")) {
            var statuses = new ArrayList<Integer>();
            while (rows.next()) {
                statuses.add(rows.getInt(1));
            }
            return statuses;
        }
    }

    private static void sql(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Intercepts(@Signature(type = Executor.class, method = "update",
        args = {MappedStatement.class, Object.class}))
    private record PauseBeforeOpen(CyclicBarrier writers) implements Interceptor {

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            if (statement.getId().equals(PlotIdleIntervalMapper.class.getName() + ".open")) {
                writers.await(10, TimeUnit.SECONDS);
            }
            return invocation.proceed();
        }
    }
}
