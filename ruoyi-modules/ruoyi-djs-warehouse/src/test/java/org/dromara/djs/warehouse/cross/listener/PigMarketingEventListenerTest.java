package org.dromara.djs.warehouse.cross.listener;

import org.dromara.djs.breed.core.event.PigMarketingEvent;
import org.dromara.djs.breed.event.slaughter.domain.PigMarketing;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PigMarketingEventListener} 单测（CROSS-FLOW-001 / D10）。
 *
 * <h3>覆盖场景</h3>
 * <ol>
 *   <li>happy path：PigMarketingEvent 触发 → BarInfo.insert 调用 1 次 + 入参字段
 *       （barId / earNo / marketingTime / marketingWeight / status='pending_singe' / inMethod=1）全对</li>
 *   <li>mapper INSERT 抛 DuplicateKeyException → listener **不**重抛 + 走 catch 路径
 *       （AFTER_COMMIT 阶段抛异常会让上游业务方误以为出栏失败）</li>
 *   <li>bar_id 业务码委托给 {@link IBizCodeGenerator}：generate(BAR_NO, empty map) 调 1 次</li>
 * </ol>
 *
 * <p>用 Mockito mock {@link BarInfoMapper} + {@link IBizCodeGenerator}，不需要 Spring 上下文。</p>
 *
 * @author djs
 * @since CROSS-FLOW-001
 */
@Tag("local")
@ExtendWith(MockitoExtension.class)
class PigMarketingEventListenerTest {

    @Mock
    private BarInfoMapper barInfoMapper;

    @Mock
    private IBizCodeGenerator bizCodeGenerator;

    @Mock
    private org.dromara.djs.warehouse.trace.service.ITraceService traceService;

    private PigMarketingEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PigMarketingEventListener(barInfoMapper, bizCodeGenerator, traceService);
    }

    /**
     * 构造 happy path PigMarketing。
     */
    private PigMarketing buildMarketing() {
        PigMarketing m = new PigMarketing();
        m.setId(2061L);
        m.setPigId(1010L);
        m.setEarNo("01A12605001");
        m.setMarketingDate(LocalDateTime.of(2026, 6, 4, 9, 30, 0));
        m.setOutWeight(new BigDecimal("125.500"));
        m.setOutDest("send_slaughter");
        return m;
    }

    @Test
    @DisplayName("happy path：触发事件后 BarInfo INSERT 一次，6 个字段全对")
    void onPigMarketing_happyPath_insertsBarInfoWithCorrectFields() {
        PigMarketing marketing = buildMarketing();
        PigMarketingEvent event = new PigMarketingEvent(this, marketing);
        when(bizCodeGenerator.generate(eq(BizCodeType.BAR_NO), anyMap()))
            .thenReturn("BAR2606040001");

        listener.onPigMarketing(event);

        ArgumentCaptor<BarInfo> captor = ArgumentCaptor.forClass(BarInfo.class);
        verify(barInfoMapper, times(1)).insert(captor.capture());
        BarInfo bar = captor.getValue();

        assertThat(bar.getBarId()).isEqualTo("BAR2606040001");
        assertThat(bar.getEarNo()).isEqualTo("01A12605001");
        assertThat(bar.getStatus()).isEqualTo("pending_singe");
        assertThat(bar.getInMethod()).isEqualTo(1);
        assertThat(bar.getMarketingWeight()).isEqualByComparingTo(new BigDecimal("125.500"));
        // LocalDateTime → Date 转换走系统时区，断言一致即可
        assertThat(bar.getMarketingTime()).isEqualTo(
            java.util.Date.from(marketing.getMarketingDate().atZone(ZoneId.systemDefault()).toInstant())
        );
    }

    @Test
    @DisplayName("INSERT 抛 DuplicateKeyException → listener 吞掉异常（AFTER_COMMIT 阶段不能影响上游）")
    void onPigMarketing_mapperThrows_listenerSwallowsException() {
        PigMarketing marketing = buildMarketing();
        PigMarketingEvent event = new PigMarketingEvent(this, marketing);
        when(bizCodeGenerator.generate(eq(BizCodeType.BAR_NO), anyMap()))
            .thenReturn("BAR2606040002");
        when(barInfoMapper.insert(any(BarInfo.class)))
            .thenThrow(new DuplicateKeyException("uk_bar_id violated"));

        // AFTER_COMMIT 阶段抛异常无意义（养殖事务已提交），listener 必须 swallow
        assertThatCode(() -> listener.onPigMarketing(event)).doesNotThrowAnyException();
        verify(barInfoMapper, times(1)).insert(any(BarInfo.class));
    }

    @Test
    @DisplayName("bar_id 业务码生成：调用 IBizCodeGenerator.generate(BAR_NO, empty map) 1 次")
    void onPigMarketing_callsBizCodeGeneratorOnce() {
        PigMarketing marketing = buildMarketing();
        PigMarketingEvent event = new PigMarketingEvent(this, marketing);
        when(bizCodeGenerator.generate(eq(BizCodeType.BAR_NO), anyMap()))
            .thenReturn("BAR2606040003");

        listener.onPigMarketing(event);

        verify(bizCodeGenerator, times(1)).generate(eq(BizCodeType.BAR_NO), eq(Map.of()));
        // INSERT 也应该走通
        verify(barInfoMapper, times(1)).insert(any(BarInfo.class));
        // 不应该走旧 inline selectMaxBarIdByDate 路径（mapper 上没该方法，编译期保护）
        verify(barInfoMapper, never()).updateStatusToPendingCut(any(), any());
    }
}
