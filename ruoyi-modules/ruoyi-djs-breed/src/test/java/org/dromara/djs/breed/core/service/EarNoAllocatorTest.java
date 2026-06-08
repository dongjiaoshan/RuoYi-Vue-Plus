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
 * {@link EarNoAllocator} 单元测试（ADR-0011 14 位客户格式）。
 *
 * <p>格式 = {@code {品系1}{品种2}{公母1}{出生yyMMdd6}{当天序号4}}，前缀定长 10 位。覆盖：</p>
 * <ul>
 *   <li>前缀组装：品系1 + 品种2 + 公母(M→1/F→2) + 出生 yyMMdd</li>
 *   <li>空前缀 → seq 从 0001 起；现存 max → +1；批量连号</li>
 *   <li>公母不同前缀隔离（同品系品种不同性别 → 独立序号桶）</li>
 *   <li>UNIQUE 兜底：候选已占用 → 重新解析 max 重试；重试耗尽 → 抛 ear_no.generate_conflict</li>
 *   <li>抢锁失败 / count≤0 → 抛 ServiceException 且不读 DB</li>
 *   <li>品系/品种/出生日空 → 抛参数异常；未知性别 → 抛异常（不默认未约定值）</li>
 * </ul>
 *
 * @author djs
 * @since ADR-0011（D12X-BRD-EARNO-FORMAT-001）
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EarNoAllocator 单元测试 (ADR-0011 14 位格式)")
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

    /** 品系=杜洛克(4) + 品种=杜洛克(04) + 公(M→1) + 260510 = 前缀 4041260510（10 位）。 */
    private String boarPrefix;
    /** 同品系品种 + 母(F→2) → 前缀 4042260510。 */
    private String sowPrefix;

    @BeforeEach
    void setup() throws InterruptedException {
        allocator = new EarNoAllocator(pigMapper, redissonClient);
        boarPrefix = "4" + "04" + "1" + YYMMDD;
        sowPrefix = "4" + "04" + "2" + YYMMDD;
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(pigMapper.existsEarNo(anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("前缀组装：品系4+品种04+公(M→1)+260510 = 4041260510，长度 10")
    void buildPrefix_boar() {
        String prefix = allocator.buildPrefix("4", "04", "M", BIRTH);
        assertThat(prefix).isEqualTo("4041260510").hasSize(10);
    }

    @Test
    @DisplayName("公母映射：F→2（母）")
    void buildPrefix_sow_sexCode2() {
        String prefix = allocator.buildPrefix("4", "04", "F", BIRTH);
        assertThat(prefix).isEqualTo("4042260510");
    }

    @Test
    @DisplayName("品种短码 '4' 左补零到 2 位 '04'")
    void buildPrefix_padsBreedCode() {
        String prefix = allocator.buildPrefix("4", "4", "M", BIRTH);
        assertThat(prefix).isEqualTo("4041260510");
    }

    @Test
    @DisplayName("空前缀（无人引过）→ 14 位耳号 seq 从 0001 起")
    void emptyPrefix_startsAt1() {
        when(pigMapper.selectMaxEarNoByPrefix(boarPrefix)).thenReturn(null);

        List<String> earNos = allocator.allocate("4", "04", "M", BIRTH, 1);

        assertThat(earNos).containsExactly(boarPrefix + "0001");
        assertThat(earNos.get(0)).hasSize(14);
    }

    @Test
    @DisplayName("现存 max=...0008 → 下号 ...0009")
    void existingMax8_next9() {
        when(pigMapper.selectMaxEarNoByPrefix(boarPrefix)).thenReturn(boarPrefix + "0008");

        List<String> earNos = allocator.allocate("4", "04", "M", BIRTH, 1);

        assertThat(earNos).containsExactly(boarPrefix + "0009");
    }

    @Test
    @DisplayName("批量 N=5 从 max=...0008 起 → 0009..0013 严格连号")
    void batch5_consecutive() {
        when(pigMapper.selectMaxEarNoByPrefix(boarPrefix)).thenReturn(boarPrefix + "0008");

        List<String> earNos = allocator.allocate("4", "04", "M", BIRTH, 5);

        assertThat(earNos).containsExactly(
            boarPrefix + "0009", boarPrefix + "0010", boarPrefix + "0011",
            boarPrefix + "0012", boarPrefix + "0013");
    }

    @Test
    @DisplayName("公母不同前缀隔离：同品系品种公猪 max=...0003、母猪空 → 母猪从 0001 起，互不影响")
    void boarSowIsolated() {
        when(pigMapper.selectMaxEarNoByPrefix(boarPrefix)).thenReturn(boarPrefix + "0003");
        when(pigMapper.selectMaxEarNoByPrefix(sowPrefix)).thenReturn(null);

        List<String> boar = allocator.allocate("4", "04", "M", BIRTH, 1);
        List<String> sow = allocator.allocate("4", "04", "F", BIRTH, 1);

        assertThat(boar).containsExactly(boarPrefix + "0004");
        assertThat(sow).containsExactly(sowPrefix + "0001");
        // 公母位（第 4 位）不同：1 vs 2
        assertThat(boar.get(0).charAt(3)).isEqualTo('1');
        assertThat(sow.get(0).charAt(3)).isEqualTo('2');
    }

    @Test
    @DisplayName("UNIQUE 兜底：首批候选被占用 → 重新解析 max（已涨）后重试成功")
    void uniqueFallback_retrySucceeds() {
        when(pigMapper.selectMaxEarNoByPrefix(boarPrefix))
            .thenReturn(boarPrefix + "0008", boarPrefix + "0009");
        when(pigMapper.existsEarNo(boarPrefix + "0009")).thenReturn(123L);
        when(pigMapper.existsEarNo(boarPrefix + "0010")).thenReturn(null);

        List<String> earNos = allocator.allocate("4", "04", "M", BIRTH, 1);

        assertThat(earNos).containsExactly(boarPrefix + "0010");
        verify(pigMapper, times(2)).selectMaxEarNoByPrefix(boarPrefix);
    }

    @Test
    @DisplayName("重试耗尽（候选一直被占用）→ 抛 ear_no.generate_conflict")
    void uniqueFallback_exhausted_throws() {
        when(pigMapper.selectMaxEarNoByPrefix(boarPrefix)).thenReturn(boarPrefix + "0008");
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
        verify(pigMapper, times(0)).selectMaxEarNoByPrefix(anyString());
    }

    @Test
    @DisplayName("抢锁超时 → 抛 ServiceException，不读 DB")
    void lockTimeout_throws() throws InterruptedException {
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> allocator.allocate("4", "04", "M", BIRTH, 1))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("抢锁超时");
        verify(pigMapper, times(0)).selectMaxEarNoByPrefix(anyString());
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
    @DisplayName("未知性别 → 抛参数异常（不默认未约定值）")
    void unknownSex_throws() {
        assertThatThrownBy(() -> allocator.allocate("4", "04", "X", BIRTH, 1))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("性别");
    }

    @Test
    @DisplayName("allocateOne 单头 → 返 14 位耳号")
    void allocateOne_returns14() {
        when(pigMapper.selectMaxEarNoByPrefix(sowPrefix)).thenReturn(null);

        String earNo = allocator.allocateOne("4", "04", "F", BIRTH);

        assertThat(earNo).isEqualTo(sowPrefix + "0001").hasSize(14);
    }

    @Test
    @DisplayName("nextSeqForPrefix：现存 max=...0008 → 返 9；空前缀 → 返 1（供 service 端首号下限校验复用）")
    void nextSeqForPrefix_resolvesNextSeq() {
        when(pigMapper.selectMaxEarNoByPrefix(boarPrefix)).thenReturn(boarPrefix + "0008");
        assertThat(allocator.nextSeqForPrefix(boarPrefix)).isEqualTo(9L);

        when(pigMapper.selectMaxEarNoByPrefix(sowPrefix)).thenReturn(null);
        assertThat(allocator.nextSeqForPrefix(sowPrefix)).isEqualTo(1L);
    }
}
