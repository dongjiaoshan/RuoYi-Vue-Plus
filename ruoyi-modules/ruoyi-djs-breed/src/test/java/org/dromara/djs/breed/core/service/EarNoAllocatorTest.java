package org.dromara.djs.breed.core.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.breed.core.mapper.PigMapper;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EarNoAllocator} 单元测试（ADR-0011 带分隔符客户格式；R47 序号按出生日全场递增）。
 *
 * <p>格式（2026-06-18 起重新编入性别段）= {@code {品系}-{品种2}-{性别1}-{出生yyMMdd6}-{当天序号3}}
 * （如 {@code 4-04-1-260510-001}，性别码 1=公 2=母）；性别空（仔猪批量混公母）回退旧格式
 * {@code {品系}-{品种2}-{yyMMdd6}-{序号3}}。覆盖：</p>
 * <ul>
 *   <li>前缀组装：品系 - 品种2 - 性别1 - 出生 yyMMdd；性别空回退无性别段</li>
 *   <li>当天无现存号 → seq 从 001 起；现存 max → +1；批量连号</li>
 *   <li>序号按<b>出生日全场</b>递增（不分品系品种）：同日不同前缀共享同一序列，互相影响 max</li>
 *   <li>UNIQUE 兜底：候选已占用 → 重新解析 max 重试；重试耗尽 → 抛 ear_no.generate_conflict</li>
 *   <li>抢锁失败 / count≤0 → 抛 ServiceException 且不读 DB</li>
 *   <li>品系/品种/出生日空 → 抛参数异常</li>
 * </ul>
 *
 * @author djs
 * @since ADR-0011（D12X-BRD-EARNO-FORMAT-001）；R47 序号按出生日全场递增
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EarNoAllocator 单元测试 (ADR-0011 带分隔符格式 + R47 出生日全场递增)")
class EarNoAllocatorTest {

    @Mock
    private PigMapper pigMapper;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    private EarNoAllocator allocator;

    /** 固定出生日 2026-05-10 → yyMMdd = 260510。 */
    private static final LocalDate BIRTH = LocalDate.of(2026, 5, 10);
    private static final String YYMMDD = "260510";

    /** 品系=杜洛克(4) - 品种=杜洛克(04) - 性别 M=1 - 260510 = 前缀 4-04-1-260510（13 字符，2026-06-18 起编入性别段）。 */
    private String boarPrefix;
    /** 同出生日不同品种品系性别（品系9-品种09-性别 F=2）→ 前缀 9-09-2-260510，序号与 boarPrefix 共享全场序列。 */
    private String otherPrefix;

    @BeforeEach
    void setup() throws InterruptedException {
        allocator = new EarNoAllocator(pigMapper, redissonClient);
        boarPrefix = "4-04-1-" + YYMMDD;
        otherPrefix = "9-09-2-" + YYMMDD;
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(pigMapper.existsEarNo(anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("前缀组装：品系4-品种04-性别M(1)-260510 = 4-04-1-260510，长度 13（2026-06-18 编入性别段）")
    void buildPrefix_basic() {
        String prefix = allocator.buildPrefix("4", "04", "M", BIRTH);
        assertThat(prefix).isEqualTo("4-04-1-260510").hasSize(13);
    }

    @Test
    @DisplayName("性别编码：F→2 / M→1，公母前缀不同（客户权威表 1=公 2=母）")
    void buildPrefix_sexEncoded() {
        assertThat(allocator.buildPrefix("4", "04", "M", BIRTH)).isEqualTo("4-04-1-260510");
        assertThat(allocator.buildPrefix("4", "04", "F", BIRTH)).isEqualTo("4-04-2-260510");
    }

    @Test
    @DisplayName("性别空（仔猪耳标批量混公母）→ 保持旧格式 4-04-260510，不编性别段（向后兼容）")
    void buildPrefix_nullSex_legacyFormat() {
        assertThat(allocator.buildPrefix("4", "04", null, BIRTH)).isEqualTo("4-04-260510").hasSize(11);
        assertThat(allocator.buildPrefix("4", "04", "", BIRTH)).isEqualTo("4-04-260510");
    }

    @Test
    @DisplayName("性别码非法 → 抛参数异常")
    void buildPrefix_invalidSex_throws() {
        assertThatThrownBy(() -> allocator.buildPrefix("4", "04", "X", BIRTH))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("性别码");
    }

    @Test
    @DisplayName("品种短码 '4' 左补零到 2 位 '04'")
    void buildPrefix_padsBreedCode() {
        String prefix = allocator.buildPrefix("4", "4", "M", BIRTH);
        assertThat(prefix).isEqualTo("4-04-1-260510");
    }

    @Test
    @DisplayName("当天无现存号 → 带分隔符耳号 seq 从 001 起，长度 17（含性别段）")
    void emptyDate_startsAt1() {
        when(pigMapper.selectMaxSeqByDateSegment(YYMMDD)).thenReturn(null);

        List<String> earNos = allocator.allocate("4", "04", "M", BIRTH, 1);

        assertThat(earNos).containsExactly(boarPrefix + "-001");
        assertThat(earNos.get(0)).hasSize(17);
    }

    @Test
    @DisplayName("当天全场 max=8 → 下号 ...-009")
    void existingMax8_next9() {
        when(pigMapper.selectMaxSeqByDateSegment(YYMMDD)).thenReturn(8L);

        List<String> earNos = allocator.allocate("4", "04", "M", BIRTH, 1);

        assertThat(earNos).containsExactly(boarPrefix + "-009");
    }

    @Test
    @DisplayName("批量 N=5 从 max=8 起 → 009..013 严格连号")
    void batch5_consecutive() {
        when(pigMapper.selectMaxSeqByDateSegment(YYMMDD)).thenReturn(8L);

        List<String> earNos = allocator.allocate("4", "04", "M", BIRTH, 5);

        assertThat(earNos).containsExactly(
            boarPrefix + "-009", boarPrefix + "-010", boarPrefix + "-011",
            boarPrefix + "-012", boarPrefix + "-013");
    }

    @Test
    @DisplayName("R47 出生日全场递增：同日不同品系品种共享序列，第二批接着第一批序号往后递增")
    void sameDateAcrossPrefixesSharesSeq() {
        // 第一批（品系4-品种04）落库后当天 max=3；第二批（品系9-品种09）同日 → 从 004 起，不重置 001
        when(pigMapper.selectMaxSeqByDateSegment(YYMMDD)).thenReturn(3L);

        List<String> other = allocator.allocate("9", "09", "F", BIRTH, 1);

        // 前缀仍按各自品系品种拼，但序号在全天范围内连续唯一（004 而非 001）
        assertThat(other).containsExactly(otherPrefix + "-004");
    }

    @Test
    @DisplayName("UNIQUE 兜底：首批候选被占用 → 重新解析 max（已涨）后重试成功")
    void uniqueFallback_retrySucceeds() {
        when(pigMapper.selectMaxSeqByDateSegment(YYMMDD)).thenReturn(8L, 9L);
        when(pigMapper.existsEarNo(boarPrefix + "-009")).thenReturn(123L);
        when(pigMapper.existsEarNo(boarPrefix + "-010")).thenReturn(null);

        List<String> earNos = allocator.allocate("4", "04", "M", BIRTH, 1);

        assertThat(earNos).containsExactly(boarPrefix + "-010");
        verify(pigMapper, times(2)).selectMaxSeqByDateSegment(YYMMDD);
    }

    @Test
    @DisplayName("重试耗尽（候选一直被占用）→ 抛 ear_no.generate_conflict")
    void uniqueFallback_exhausted_throws() {
        when(pigMapper.selectMaxSeqByDateSegment(YYMMDD)).thenReturn(8L);
        when(pigMapper.existsEarNo(anyString())).thenReturn(999L);

        assertThatThrownBy(() -> allocator.allocate("4", "04", "M", BIRTH, 1))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ear_no.generate_conflict");
    }

    @Test
    @DisplayName("count <= 0 → 抛参数异常，不抢锁不读 DB")
    void invalidCount_throws() {
        assertThatThrownBy(() -> allocator.allocate("4", "04", "M", BIRTH, 0))
            .isInstanceOf(ServiceException.class);
        verify(pigMapper, times(0)).selectMaxSeqByDateSegment(anyString());
    }

    @Test
    @DisplayName("抢锁超时 → 抛 ServiceException，不读 DB")
    void lockTimeout_throws() throws InterruptedException {
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> allocator.allocate("4", "04", "M", BIRTH, 1))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("抢锁超时");
        verify(pigMapper, times(0)).selectMaxSeqByDateSegment(anyString());
    }

    @Test
    @DisplayName("品系码空 → 抛参数异常")
    void blankStrain_throws() {
        assertThatThrownBy(() -> allocator.allocate("", "04", "M", BIRTH, 1))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("品系码");
    }

    @Test
    @DisplayName("品种码空 → 抛参数异常")
    void blankBreed_throws() {
        assertThatThrownBy(() -> allocator.allocate("4", "", "M", BIRTH, 1))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("品种码");
    }

    @Test
    @DisplayName("出生日期空 → 抛参数异常")
    void nullBirthDate_throws() {
        assertThatThrownBy(() -> allocator.allocate("4", "04", "M", null, 1))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("出生日期");
    }

    @Test
    @DisplayName("allocateOne 单头 → 返带分隔符耳号，长度 17（含性别段）")
    void allocateOne_returns17() {
        when(pigMapper.selectMaxSeqByDateSegment(YYMMDD)).thenReturn(null);

        String earNo = allocator.allocateOne("4", "04", "M", BIRTH);

        assertThat(earNo).isEqualTo(boarPrefix + "-001").hasSize(17);
    }

    @Test
    @DisplayName("nextSeqForDate：当天全场 max=8 → 返 9；当天无号 → 返 1（供 service 端首号下限校验 / 预览复用）")
    void nextSeqForDate_resolvesNextSeq() {
        when(pigMapper.selectMaxSeqByDateSegment(YYMMDD)).thenReturn(8L);
        assertThat(allocator.nextSeqForDate(BIRTH)).isEqualTo(9L);

        when(pigMapper.selectMaxSeqByDateSegment(YYMMDD)).thenReturn(null);
        assertThat(allocator.nextSeqForDate(BIRTH)).isEqualTo(1L);
    }

    @Test
    @DisplayName("nextSeqForDate：出生日空 → 抛参数异常")
    void nextSeqForDate_nullBirthDate_throws() {
        assertThatThrownBy(() -> allocator.nextSeqForDate(null))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("出生日期");
    }
}
