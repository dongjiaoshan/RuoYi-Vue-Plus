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
import java.time.format.DateTimeFormatter;
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
 * {@link EarNoAllocator} 单元测试（BRD-FIX-EARNO-001）。
 *
 * <p>覆盖序号源 DB max 推算的核心场景：</p>
 * <ul>
 *   <li>空前缀（无人引过）→ seq 从 1 起</li>
 *   <li>现存 max=...0008 → 下号 ...0009</li>
 *   <li>批量 N=5 → 连号严格 +1</li>
 *   <li>解析旧 3 位 / 新 4 位 seq 兼容</li>
 *   <li>UNIQUE 兜底：候选已占用 → 重新解析 max + 重试</li>
 *   <li>重试耗尽 → 抛 ServiceException(ear_no.generate_conflict)</li>
 *   <li>抢锁失败 → 抛 ServiceException + 不读 DB</li>
 * </ul>
 *
 * @author djs
 * @since BRD-FIX-EARNO-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EarNoAllocator 单元测试 (BRD-FIX-EARNO-001)")
class EarNoAllocatorTest {

    @Mock
    private PigMapper pigMapper;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    private EarNoAllocator allocator;

    /** 当前月前缀 farm01 + barn01 + yyMM。 */
    private String yyMM;
    private String prefix;

    @BeforeEach
    void setup() throws InterruptedException {
        allocator = new EarNoAllocator(pigMapper, redissonClient);
        yyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMM"));
        prefix = "0101" + yyMM;
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        // 默认候选都没被占用
        when(pigMapper.existsEarNo(anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("空前缀（无人引过）→ seq 从 0001 起")
    void emptyPrefix_startsAt1() {
        when(pigMapper.selectMaxEarNoByPrefix(prefix)).thenReturn(null);

        List<String> earNos = allocator.allocate("01", "01", 1);

        assertThat(earNos).containsExactly(prefix + "0001");
    }

    @Test
    @DisplayName("现存 max=...0008 → 下号 ...0009")
    void existingMax8_next9() {
        when(pigMapper.selectMaxEarNoByPrefix(prefix)).thenReturn(prefix + "0008");

        List<String> earNos = allocator.allocate("01", "01", 1);

        assertThat(earNos).containsExactly(prefix + "0009");
    }

    @Test
    @DisplayName("批量 N=5 从 max=...0008 起 → 0009..0013 严格连号")
    void batch5_consecutive() {
        when(pigMapper.selectMaxEarNoByPrefix(prefix)).thenReturn(prefix + "0008");

        List<String> earNos = allocator.allocate("01", "01", 5);

        assertThat(earNos).containsExactly(
            prefix + "0009", prefix + "0010", prefix + "0011",
            prefix + "0012", prefix + "0013");
    }

    @Test
    @DisplayName("解析旧 3 位 seq（历史耳号 ...008）→ 下号补 4 位 ...0009")
    void parseLegacy3DigitSeq() {
        when(pigMapper.selectMaxEarNoByPrefix(prefix)).thenReturn(prefix + "008");

        List<String> earNos = allocator.allocate("01", "01", 1);

        assertThat(earNos).containsExactly(prefix + "0009");
    }

    @Test
    @DisplayName("UNIQUE 兜底：首批候选被占用 → 重新解析 max（已涨到 9）后重试成功")
    void uniqueFallback_retrySucceeds() {
        // 第一次解析 max=...0008 → 候选 0009 被占；第二次解析 max=...0009 → 候选 0010 空
        when(pigMapper.selectMaxEarNoByPrefix(prefix))
            .thenReturn(prefix + "0008", prefix + "0009");
        when(pigMapper.existsEarNo(prefix + "0009")).thenReturn(123L);  // 已占用
        when(pigMapper.existsEarNo(prefix + "0010")).thenReturn(null);  // 空闲

        List<String> earNos = allocator.allocate("01", "01", 1);

        assertThat(earNos).containsExactly(prefix + "0010");
        // 重新解析了 2 次 max
        verify(pigMapper, times(2)).selectMaxEarNoByPrefix(prefix);
    }

    @Test
    @DisplayName("重试耗尽（候选一直被占用）→ 抛 ear_no.generate_conflict")
    void uniqueFallback_exhausted_throws() {
        when(pigMapper.selectMaxEarNoByPrefix(prefix)).thenReturn(prefix + "0008");
        when(pigMapper.existsEarNo(anyString())).thenReturn(999L);  // 永远被占

        assertThatThrownBy(() -> allocator.allocate("01", "01", 1))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("ear_no.generate_conflict");
    }

    @Test
    @DisplayName("count <= 0 → 抛参数异常，不抢锁不读 DB")
    void invalidCount_throws() {
        assertThatThrownBy(() -> allocator.allocate("01", "01", 0))
            .isInstanceOf(ServiceException.class);
        verify(pigMapper, times(0)).selectMaxEarNoByPrefix(anyString());
    }

    @Test
    @DisplayName("抢锁超时 → 抛 ServiceException，不读 DB")
    void lockTimeout_throws() throws InterruptedException {
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> allocator.allocate("01", "01", 1))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("抢锁超时");
        verify(pigMapper, times(0)).selectMaxEarNoByPrefix(anyString());
    }

    @Test
    @DisplayName("barnCode 长码 B7-PROD → 前缀截 2 位 B7；farmCode blank → 回落 01")
    void normalizesCodeSegments() {
        String p = "01B7" + yyMM;
        when(pigMapper.selectMaxEarNoByPrefix(p)).thenReturn(null);

        List<String> earNos = allocator.allocate("", "B7-PROD", 1);

        assertThat(earNos).containsExactly(p + "0001");
    }
}
