package org.dromara.djs.common.encoder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link BizCodeGeneratorImpl} 单元测试（SYS-INFRA-004）。
 *
 * <h3>覆盖项</h3>
 * <ol>
 *   <li>format — 占位符替换正确（耳号 / 追溯码两种典型 pattern）</li>
 *   <li>100 并发 — 全 100 个耳号唯一（用本地 ReentrantLock 模拟 Redisson 分布式锁 + AtomicLong 模拟序号表）</li>
 *   <li>批量 — 连续序号且数量正确</li>
 *   <li>跨日重置 — daily_reset=1 时，seqDate 变化触发新序号行</li>
 *   <li>PRODUCE_NO — daily_reset=3 按业态前缀（Z/G/B/H/D/L）分桶，6 前缀互不串号</li>
 *   <li>RETURN_NO — daily_reset=1，RET{yyyyMMdd}{seq4} 连续递增</li>
 * </ol>
 *
 * <p>不启 Spring；用 Mockito mock {@code BizCodeRuleMapper / BizCodeSequenceMapper / RedissonClient}，
 * 在 mock 上挂自定义行为（{@link AtomicLong} 模拟序号 + {@link ReentrantLock} 模拟分布式锁），
 * 这样既能验证并发安全的真实路径（锁 + 序号原子操作），又不依赖外部 Redis/MySQL。</p>
 *
 * @author djs
 * @since SYS-INFRA-004
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BizCodeGeneratorImpl 单元测试")
class BizCodeGeneratorImplTest {

    @Mock
    private BizCodeRuleMapper ruleMapper;

    @Mock
    private BizCodeSequenceMapper sequenceMapper;

    @Mock
    private RedissonClient redissonClient;

    private BizCodeGeneratorImpl generator;

    /**
     * 全局序号桶：key = "codeType:seqDate" → AtomicLong；模拟数据库的 current_seq 列。
     */
    private final Map<String, AtomicLong> seqStore = new ConcurrentHashMap<>();

    /**
     * 全局锁桶：key = lockKey → ReentrantLock；模拟 Redisson 的 getLock(同名 key) 返回同一把锁的语义。
     */
    private final Map<String, ReentrantLock> lockStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        generator = new BizCodeGeneratorImpl(ruleMapper, sequenceMapper, redissonClient);

        // --- mock 锁：同 key 总返回同一把 ReentrantLock，确保并发时真的串行 ---
        when(redissonClient.getLock(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            ReentrantLock realLock = lockStore.computeIfAbsent(key, k -> new ReentrantLock());
            return wrapAsRLock(realLock);
        });

        // --- mock 序号 mapper：incrementSeq + selectCurrentSeq 联合实现原子计数 ---
        when(sequenceMapper.incrementSeq(anyString(), anyString(), anyLong())).thenAnswer(inv -> {
            String codeType = inv.getArgument(0);
            String seqDate = inv.getArgument(1);
            long step = inv.getArgument(2);
            String key = codeType + ":" + seqDate;
            AtomicLong counter = seqStore.get(key);
            if (counter == null) {
                // 行不存在，返 0 让上层走 INSERT 分支
                return 0;
            }
            counter.addAndGet(step);
            return 1;
        });
        when(sequenceMapper.selectCurrentSeq(anyString(), anyString())).thenAnswer(inv -> {
            String codeType = inv.getArgument(0);
            String seqDate = inv.getArgument(1);
            AtomicLong counter = seqStore.get(codeType + ":" + seqDate);
            return counter == null ? null : counter.get();
        });
        when(sequenceMapper.insert(any(BizCodeSequence.class))).thenAnswer(inv -> {
            BizCodeSequence init = inv.getArgument(0);
            String key = init.getCodeType() + ":" + init.getSeqDate();
            // putIfAbsent 模拟 UNIQUE 约束语义：已存在时抛 DuplicateKeyException
            AtomicLong existing = seqStore.putIfAbsent(key, new AtomicLong(init.getCurrentSeq()));
            if (existing != null) {
                throw new org.springframework.dao.DuplicateKeyException("UNIQUE conflict: " + key);
            }
            return 1;
        });
    }

    // ============================================================
    // 1. format — pattern 占位符替换正确
    // ============================================================

    @Test
    @DisplayName("format: EAR_NO pattern 占位符全部替换")
    void format_earNo_pattern() {
        BizCodeRule rule = newRule("EAR_NO", "{farmCode2}{barnCode2}{yyMM}{dailySeq4}", 1);
        Map<String, Object> ctx = Map.of("farmCode", "01", "barnCode", "A1");

        String code = generator.format(rule, ctx, 5L);

        String expectedYyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));
        assertThat(code).isEqualTo("01A1" + expectedYyMM + "0005");
    }

    @Test
    @DisplayName("format: TRACE_CODE pattern 含 6 位序号 + productCode2 + yyyyMMdd")
    void format_traceCode_pattern() {
        BizCodeRule rule = newRule("TRACE_CODE", "T{yyyyMMdd}{productCode2}{seq6}", 0);
        Map<String, Object> ctx = Map.of("productCode", "PG");

        String code = generator.format(rule, ctx, 1234L);

        String expectedYmd = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        assertThat(code).isEqualTo("T" + expectedYmd + "PG001234");
    }

    @Test
    @DisplayName("format: 缺字段时占位符保留 + warn（不抛）")
    void format_missingContext_keepsPlaceholder() {
        BizCodeRule rule = newRule("EAR_NO", "{farmCode2}{barnCode2}{yyMM}{dailySeq4}", 1);
        Map<String, Object> ctx = Map.of("farmCode", "01"); // 缺 barnCode

        String code = generator.format(rule, ctx, 1L);

        assertThat(code).contains("{barnCode2}");
    }

    // ============================================================
    // 2. 100 并发 — 全 100 个耳号唯一
    // ============================================================

    @Test
    @DisplayName("100 并发生成耳号 → 全部唯一（无碰撞）")
    void ear_no_no_collision_under_concurrency() throws Exception {
        // 准备规则：EAR_NO 每日重置
        stubRule("EAR_NO", "{farmCode2}{barnCode2}{yyMM}{dailySeq4}", 1);

        final int total = 100;
        final int threadPoolSize = 20;
        Set<String> codes = ConcurrentHashMap.newKeySet();
        ExecutorService exec = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(total);

        for (int i = 0; i < total; i++) {
            exec.submit(() -> {
                try {
                    start.await();
                    String code = generator.generate(BizCodeType.EAR_NO,
                        Map.of("farmCode", "01", "barnCode", "A1"));
                    codes.add(code);
                } catch (Throwable t) {
                    // 任何异常都视为测试失败（codes.size 不达 100）
                    t.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        exec.shutdownNow();

        assertThat(finished).as("100 个生成在 30s 内完成").isTrue();
        assertThat(codes).as("100 个编码全部唯一").hasSize(total);
    }

    // ============================================================
    // 3. 批量 — 连续序号
    // ============================================================

    @Test
    @DisplayName("批量生成 10 个 → 序号连续 + 数量正确")
    void batch_generate_continuous_seq() {
        stubRule("EAR_NO", "{farmCode2}{barnCode2}{yyMM}{dailySeq4}", 1);

        List<String> first = generator.generateBatch(BizCodeType.EAR_NO,
            Map.of("farmCode", "01", "barnCode", "A1"), 10);

        assertThat(first).hasSize(10);
        // 提取每个 code 最后 4 位序号
        String yyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));
        for (int i = 0; i < 10; i++) {
            String expectedSeq = String.format("%04d", i + 1);
            assertThat(first.get(i)).isEqualTo("01A1" + yyMM + expectedSeq);
        }

        // 再批量一次，序号从 11 开始
        List<String> second = generator.generateBatch(BizCodeType.EAR_NO,
            Map.of("farmCode", "01", "barnCode", "A1"), 5);
        assertThat(second).hasSize(5);
        assertThat(second.get(0)).isEqualTo("01A1" + yyMM + "0011");
        assertThat(second.get(4)).isEqualTo("01A1" + yyMM + "0015");
    }

    // ============================================================
    // 4. 跨日重置 — daily_reset=1 时换 seqDate 行
    // ============================================================

    @Test
    @DisplayName("跨日重置: 不同 seqDate 走独立计数 bucket，序号从 1 重新开始")
    void daily_reset_seq() {
        stubRule("SHIP_NO", "S{yyyyMMdd}{seq4}", 1);

        // 第一天生成 3 个（今天 seqDate）
        for (int i = 0; i < 3; i++) {
            generator.generate(BizCodeType.SHIP_NO, Map.of());
        }
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        AtomicLong todayCounter = seqStore.get("SHIP_NO:" + today);
        assertThat(todayCounter).isNotNull();
        assertThat(todayCounter.get()).isEqualTo(3L);

        // 模拟"昨天"已有的桶不影响今天 —— 直接 put 一条假数据（用一个肯定不是今天的日期串）
        String fakeYesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
        seqStore.put("SHIP_NO:" + fakeYesterday, new AtomicLong(999L));

        // 今天再生成 1 个，序号应该是 4（today bucket），而不是 1000
        String nextCode = generator.generate(BizCodeType.SHIP_NO, Map.of());
        assertThat(nextCode).isEqualTo("S" + today + "0004");
        assertThat(todayCounter.get()).isEqualTo(4L);

        // 再断言：fakeYesterday 桶完全没被本次生成动到（验证 daily_reset 用了今天的 seqDate）
        assertThat(seqStore.get("SHIP_NO:" + fakeYesterday).get()).isEqualTo(999L);
    }

    // ============================================================
    // 5. PRODUCE_NO — 6 业态前缀分桶（daily_reset=3）
    // ============================================================

    @Test
    @DisplayName("PRODUCE_NO: 6 业态前缀各自生成 yyMMdd+prefix+0001，互不串号")
    void produce_no_six_prefixes_independent_buckets() {
        stubRule("PRODUCE_NO", "{yyMMdd}{prefix}{seq4}", 3);
        String yyMMdd = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));

        // 6 业态前缀：Z 猪肉 / G 果蔬 / B 白条 / H 干货 / D 鸡蛋 / L 礼盒
        for (String prefix : List.of("Z", "G", "B", "H", "D", "L")) {
            String code = generator.generate(BizCodeType.PRODUCE_NO, Map.of("prefix", prefix));
            // 每个前缀当日首个都从 0001 起算（独立 seqDate 分桶）
            assertThat(code).isEqualTo(yyMMdd + prefix + "0001");
        }

        // 同前缀再取一次 → 0002（验证同桶递增）；其他前缀不受影响
        String z2 = generator.generate(BizCodeType.PRODUCE_NO, Map.of("prefix", "Z"));
        assertThat(z2).isEqualTo(yyMMdd + "Z0002");
        String g2 = generator.generate(BizCodeType.PRODUCE_NO, Map.of("prefix", "G"));
        assertThat(g2).isEqualTo(yyMMdd + "G0002");
    }

    @Test
    @DisplayName("PRODUCE_NO: 同前缀连续 3 个序号连续递增")
    void produce_no_same_prefix_sequential() {
        stubRule("PRODUCE_NO", "{yyMMdd}{prefix}{seq4}", 3);
        String yyMMdd = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));

        for (int i = 1; i <= 3; i++) {
            String code = generator.generate(BizCodeType.PRODUCE_NO, Map.of("prefix", "H"));
            assertThat(code).isEqualTo(yyMMdd + "H" + String.format("%04d", i));
        }
    }

    // ============================================================
    // 6. RETURN_NO — 每日重置 RET{yyyyMMdd}{seq4}
    // ============================================================

    @Test
    @DisplayName("RETURN_NO: 生成 RET+yyyyMMdd+0001，连续递增")
    void return_no_daily_reset_format() {
        stubRule("RETURN_NO", "RET{yyyyMMdd}{seq4}", 1);
        String yyyyMMdd = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        String first = generator.generate(BizCodeType.RETURN_NO, Map.of());
        assertThat(first).isEqualTo("RET" + yyyyMMdd + "0001");

        String second = generator.generate(BizCodeType.RETURN_NO, Map.of());
        assertThat(second).isEqualTo("RET" + yyyyMMdd + "0002");
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 直接构造一条规则（用于 format 单测）。 */
    private BizCodeRule newRule(String codeType, String pattern, int dailyReset) {
        BizCodeRule rule = new BizCodeRule();
        rule.setId(1L);
        rule.setTenantId("1001");
        rule.setCodeType(codeType);
        rule.setPattern(pattern);
        rule.setDailyReset(dailyReset);
        rule.setSeqLength(4);
        rule.setStatus("0");
        return rule;
    }

    /** mock ruleMapper.selectOne(...) 返回指定规则。 */
    private void stubRule(String codeType, String pattern, int dailyReset) {
        BizCodeRule rule = newRule(codeType, pattern, dailyReset);
        lenient().when(ruleMapper.selectOne(any())).thenReturn(rule);
    }

    /**
     * 用一个最薄的 RLock 代理把 ReentrantLock 包装成 RLock，使 generator 的 tryLock/unlock/isHeldByCurrentThread
     * 调用路径打通；其他 RLock 方法未使用，抛 UnsupportedOperationException 即可。
     */
    private RLock wrapAsRLock(ReentrantLock real) {
        return (RLock) java.lang.reflect.Proxy.newProxyInstance(
            RLock.class.getClassLoader(),
            new Class[]{RLock.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "tryLock":
                        // 签名 tryLock(long waitTime, long leaseTime, TimeUnit unit)
                        long waitTime = (long) args[0];
                        TimeUnit unit = (TimeUnit) args[2];
                        return real.tryLock(waitTime, unit);
                    case "unlock":
                        real.unlock();
                        return null;
                    case "isHeldByCurrentThread":
                        return real.isHeldByCurrentThread();
                    case "getName":
                        return "mockRLock";
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "MockRLock";
                    default:
                        throw new UnsupportedOperationException("Mock RLock method not implemented: " + method.getName());
                }
            }
        );
    }

    /** 兜底：避免未读 import 报错。 */
    @SuppressWarnings("unused")
    private static Field UNUSED_FIELD_PLACEHOLDER = null;
}
