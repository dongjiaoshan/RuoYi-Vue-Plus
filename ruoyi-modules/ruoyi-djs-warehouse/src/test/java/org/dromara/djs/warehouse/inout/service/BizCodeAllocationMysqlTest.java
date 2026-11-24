package org.dromara.djs.warehouse.inout.service;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.dromara.djs.common.encoder.BizCodeGeneratorImpl;
import org.dromara.djs.common.encoder.BizCodeRule;
import org.dromara.djs.common.encoder.BizCodeRuleMapper;
import org.dromara.djs.common.encoder.BizCodeSequenceMapper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real MySQL + Spring transactions + actual sequence mapper with tenant rewriting.
 * Only rules and the RLock transport are adapted in-process; no live global rule or sequence is changed.
 * Existing private sequence rows isolate the long-outer-transaction regression from cold-start INSERT behavior.
 */
@Tag("local")
@Tag("dev")
@EnabledIfEnvironmentVariable(named = "DJS_STAT_MYSQL_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:(3306|3316)/.*")
@Timeout(value = 25, unit = TimeUnit.SECONDS)
class BizCodeAllocationMysqlTest {
    private static final String TENANT_A = "Q1010_SEQ_A";
    private static final String TENANT_B = "Q1010_SEQ_B";
    private static final AtomicLong IDS = new AtomicLong(9311109900000000L);
    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private JdbcTemplate jdbc;
    private TransactionTemplate outer;
    private BizCodeGeneratorImpl generator;
    private String privateCodeType;

    @BeforeEach
    void setup() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            System.getenv("DJS_STAT_MYSQL_URL"), System.getenv("DJS_STAT_MYSQL_USER"),
            System.getenv("DJS_STAT_MYSQL_PASSWORD"));
        Properties properties = new Properties();
        // Bound the failing old implementation's row-lock wait; successful allocation should not need to wait.
        properties.setProperty("sessionVariables", "innodb_lock_wait_timeout=2");
        dataSource.setConnectionProperties(properties);
        jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        outer = new TransactionTemplate(transactions);
        privateCodeType = "Q1010_SEQ_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        for (String tenant : List.of(TENANT_A, TENANT_B)) {
            jdbc.update("INSERT INTO t_md_biz_code_sequence "
                    + "(id,tenant_id,code_type,seq_date,current_seq,del_flag,del_unique) VALUES (?,?,?,'',0,'0',0)",
                IDS.incrementAndGet(), tenant, privateCodeType);
        }

        Configuration configuration = new Configuration(new Environment("private-code-sequence-mysql",
            new SpringManagedTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                String tenant = TENANT.get();
                if (tenant == null) throw new IllegalStateException("Private test tenant must be set");
                return new StringValue(tenant);
            }
        }));
        configuration.addInterceptor(interceptor);
        configuration.addMapper(BizCodeSequenceMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        BizCodeSequenceMapper sequence = new SqlSessionTemplate(factory).getMapper(BizCodeSequenceMapper.class);
        BizCodeRuleMapper rules = mock(BizCodeRuleMapper.class);
        when(rules.selectOne(any())).thenAnswer(invocation -> rule(TENANT.get()));
        RedissonClient redis = mock(RedissonClient.class);
        Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
        when(redis.getLock(anyString())).thenAnswer(invocation ->
            localLock(locks.computeIfAbsent(invocation.getArgument(0), key -> new ReentrantLock())));
        generator = new BizCodeGeneratorImpl(rules, sequence, redis, transactions);
    }

    @AfterEach
    void cleanupPrivateSequences() {
        TENANT.remove();
        if (jdbc != null && privateCodeType != null) {
            jdbc.update("DELETE FROM t_md_biz_code_sequence WHERE code_type = ? AND tenant_id IN (?, ?)",
                privateCodeType, TENANT_A, TENANT_B);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_md_biz_code_sequence WHERE code_type = ?",
                Long.class, privateCodeType)).isZero();
        }
    }

    @Test
    @DisplayName("T1外层未提交时T2仍能取号，T1再次取号成功，外层回滚不复用已分配号")
    void independentAllocationBreaksRedisAndOuterDatabaseLockCycle() throws Exception {
        CountDownLatch firstAllocated = new CountDownLatch(1);
        CountDownLatch continueFirst = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> {
                TENANT.set(TENANT_A);
                try {
                    return outer.execute(status -> {
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                        String one = generator.generate(BizCodeType.STOCK_FLOW_NO, Map.of());
                        firstAllocated.countDown();
                        await(continueFirst);
                        String three = generator.generate(BizCodeType.STOCK_FLOW_NO, Map.of());
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                        status.setRollbackOnly();
                        return List.of(one, three);
                    });
                } finally {
                    TENANT.remove();
                }
            });
            assertThat(firstAllocated.await(5, TimeUnit.SECONDS)).isTrue();
            // A separate connection sees the committed allocation while T1's outer transaction remains open.
            assertThat(current(TENANT_A)).isEqualTo(1L);
            var second = workers.submit(() -> {
                TENANT.set(TENANT_A);
                try {
                    return outer.execute(status -> {
                        String code = generator.generate(BizCodeType.STOCK_FLOW_NO, Map.of());
                        status.setRollbackOnly();
                        return code;
                    });
                } finally {
                    TENANT.remove();
                }
            });
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(code(TENANT_A, 2));
            continueFirst.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).containsExactly(code(TENANT_A, 1), code(TENANT_A, 3));
            assertThat(current(TENANT_A)).isEqualTo(3L);
            TENANT.set(TENANT_A);
            assertThat(generator.generate(BizCodeType.STOCK_FLOW_NO, Map.of())).isEqualTo(code(TENANT_A, 4));
            assertThat(current(TENANT_A)).isEqualTo(4L);
        } finally {
            continueFirst.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("批量一次分配连续号段，业务回滚后下一批不复用")
    void batchIntervalCommitsBeforeOuterRollback() {
        TENANT.set(TENANT_A);
        List<String> first = outer.execute(status -> {
            List<String> codes = generator.generateBatch(BizCodeType.STOCK_FLOW_NO, Map.of(), 5);
            status.setRollbackOnly();
            return codes;
        });
        assertThat(first).containsExactlyElementsOf(LongStream.rangeClosed(1, 5).mapToObj(i -> code(TENANT_A, i)).toList());
        assertThat(current(TENANT_A)).isEqualTo(5L);
        assertThat(generator.generateBatch(BizCodeType.STOCK_FLOW_NO, Map.of(), 3))
            .containsExactly(code(TENANT_A, 6), code(TENANT_A, 7), code(TENANT_A, 8));
        assertThat(current(TENANT_A)).isEqualTo(8L);
    }

    @Test
    @DisplayName("独立短事务保留租户上下文，tenant序号不互相递增")
    void tenantBucketsRemainIsolatedInRequiresNewTransaction() {
        TENANT.set(TENANT_A);
        assertThat(generator.generateBatch(BizCodeType.STOCK_FLOW_NO, Map.of(), 3))
            .containsExactly(code(TENANT_A, 1), code(TENANT_A, 2), code(TENANT_A, 3));
        TENANT.set(TENANT_B);
        assertThat(generator.generateBatch(BizCodeType.STOCK_FLOW_NO, Map.of(), 2))
            .containsExactly(code(TENANT_B, 1), code(TENANT_B, 2));
        TENANT.set(TENANT_A);
        assertThat(generator.generate(BizCodeType.STOCK_FLOW_NO, Map.of())).isEqualTo(code(TENANT_A, 4));
        assertThat(current(TENANT_A)).isEqualTo(4L);
        assertThat(current(TENANT_B)).isEqualTo(2L);
    }

    private long current(String tenant) {
        return jdbc.queryForObject("SELECT current_seq FROM t_md_biz_code_sequence "
            + "WHERE code_type = ? AND tenant_id = ? AND seq_date = '' AND del_flag = '0'",
            Long.class, privateCodeType, tenant);
    }

    private BizCodeRule rule(String tenant) {
        BizCodeRule rule = new BizCodeRule();
        rule.setTenantId(tenant);
        rule.setCodeType(privateCodeType);
        rule.setPattern("Q" + tenant + "-{seq6}");
        rule.setDailyReset(0);
        rule.setStatus("0");
        return rule;
    }

    private static String code(String tenant, long sequence) {
        return "Q" + tenant + "-" + String.format("%06d", sequence);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Private test latch timeout");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Private test interrupted", error);
        }
    }

    private static RLock localLock(ReentrantLock lock) {
        return (RLock) Proxy.newProxyInstance(RLock.class.getClassLoader(), new Class<?>[]{RLock.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "tryLock" -> lock.tryLock((long) args[0], (TimeUnit) args[2]);
                case "isHeldByCurrentThread" -> lock.isHeldByCurrentThread();
                case "unlock" -> { lock.unlock(); yield null; }
                case "getName" -> "private-local-sequence-lock";
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "PrivateLocalSequenceLock";
                default -> throw new UnsupportedOperationException("Unused test RLock method: " + method.getName());
            });
    }
}
