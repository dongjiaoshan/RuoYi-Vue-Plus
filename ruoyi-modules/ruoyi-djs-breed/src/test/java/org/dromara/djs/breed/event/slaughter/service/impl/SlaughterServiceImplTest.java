package org.dromara.djs.breed.event.slaughter.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.OssService;
import org.dromara.djs.breed.core.domain.Pig;
import org.dromara.djs.breed.core.domain.bo.PigEventBo;
import org.dromara.djs.breed.core.enums.PigLifecycle;
import org.dromara.djs.breed.core.enums.PigStatusEvent;
import org.dromara.djs.breed.core.event.PigMarketingEvent;
import org.dromara.djs.breed.core.mapper.PigMapper;
import org.dromara.djs.breed.core.service.IPigCoreService;
import org.dromara.djs.breed.event.slaughter.domain.PigMarketing;
import org.dromara.djs.breed.event.slaughter.domain.bo.SlaughterBatchBo;
import org.dromara.djs.breed.event.slaughter.domain.bo.SlaughterBo;
import org.dromara.djs.breed.event.slaughter.domain.vo.SlaughterBatchPigVo;
import org.dromara.djs.breed.event.slaughter.mapper.PigMarketingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SlaughterServiceImpl} 单元测试（BRD-EVENT-004 SLAUGHTER）。
 *
 * <p>覆盖：INSERT marketing → fireEvent(SLAUGHTER) → publishEvent(PigMarketingEvent) 三步同事务。</p>
 *
 * @author djs
 * @since BRD-EVENT-004
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SlaughterServiceImpl 单元测试 (BRD-EVENT-004)")
class SlaughterServiceImplTest {

    @Mock
    private PigMarketingMapper marketingMapper;
    @Mock
    private PigMapper pigMapper;
    @Mock
    private IPigCoreService pigCoreService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private OssService ossService;

    private SlaughterServiceImpl service;

    @BeforeEach
    void setup() {
        service = new SlaughterServiceImpl(marketingMapper, pigMapper, pigCoreService, eventPublisher, ossService);
    }

    private Pig mkFatteningPig() {
        Pig p = new Pig();
        p.setId(500L);
        p.setEarNo("260520-S-001");
        p.setPigSex("M");
        p.setPigType("fattening");
        p.setCurrentStatus("");
        return p;
    }

    private SlaughterBo mkBo(Long pigId, String ossIds, BigDecimal weight) {
        SlaughterBo bo = new SlaughterBo();
        bo.setPigId(pigId);
        bo.setMarketingDate(LocalDateTime.of(2026, 5, 27, 16, 0));
        bo.setOutWeight(weight);
        bo.setOutDest("外销");
        bo.setOssIds(ossIds);
        return bo;
    }

    @Test
    @DisplayName("happy: SLAUGHTER → INSERT marketing + fireEvent(SLAUGHTER) + publishEvent(PigMarketingEvent)")
    void happyPath() {
        Pig pig = mkFatteningPig();
        when(pigMapper.selectById(500L)).thenReturn(pig);

        SlaughterBo bo = mkBo(500L, "5001,5002", new BigDecimal("125.50"));
        bo.setOperator("2061591133665759233");   // EmployeePicker 所选 userId（19 位雪花 string）
        service.recordSlaughter(bo);

        ArgumentCaptor<PigMarketing> m = ArgumentCaptor.forClass(PigMarketing.class);
        verify(marketingMapper, times(1)).insert(m.capture());
        assertThat(m.getValue().getOutWeight()).isEqualByComparingTo("125.50");
        assertThat(m.getValue().getOutDest()).isEqualTo("外销");
        assertThat(m.getValue().getOssIds()).isEqualTo("5001,5002");
        // D1=a：EmployeePicker 所选 operator userId 原样写入（snowflake string，不截断）
        assertThat(m.getValue().getOperator()).isEqualTo("2061591133665759233");

        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(1)).fireEvent(ev.capture());
        assertThat(ev.getValue().getEventType()).isEqualTo(PigStatusEvent.SLAUGHTER);

        ArgumentCaptor<PigMarketingEvent> pe = ArgumentCaptor.forClass(PigMarketingEvent.class);
        verify(eventPublisher, times(1)).publishEvent(pe.capture());
        assertThat(pe.getValue().getMarketing()).isNotNull();
        assertThat(pe.getValue().getMarketing().getOutWeight()).isEqualByComparingTo("125.50");
    }

    @Test
    @DisplayName("校验: ossIds 空 → ServiceException，不 INSERT / 不 fireEvent / 不 publish")
    void validate_photo_required() {
        Pig pig = mkFatteningPig();
        when(pigMapper.selectById(500L)).thenReturn(pig);

        SlaughterBo bo = mkBo(500L, "", new BigDecimal("125.50"));
        assertThatThrownBy(() -> service.recordSlaughter(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("slaughter.photo");
        verify(marketingMapper, never()).insert(any(PigMarketing.class));
        verify(pigCoreService, never()).fireEvent(any());
        verify(eventPublisher, never()).publishEvent(any(PigMarketingEvent.class));
    }

    private Pig mkPig(long id, String earNo) {
        Pig p = new Pig();
        p.setId(id);
        p.setEarNo(earNo);
        p.setPigSex("M");
        p.setPigType("fattening");
        p.setCurrentStatus("");
        return p;
    }

    private SlaughterBatchBo.Item mkItem(long pigId, String weight) {
        SlaughterBatchBo.Item it = new SlaughterBatchBo.Item();
        it.setPigId(pigId);
        it.setOutWeight(new BigDecimal(weight));
        return it;
    }

    private SlaughterBatchBo mkBatchBo(List<SlaughterBatchBo.Item> items, String ossIds) {
        SlaughterBatchBo bo = new SlaughterBatchBo();
        bo.setItems(items);
        bo.setMarketingDate(LocalDateTime.of(2026, 8, 17, 9, 0));
        bo.setOutDest("slaughter");
        bo.setOssIds(ossIds);
        bo.setOperator("2061591133665759233");
        return bo;
    }

    @Test
    @DisplayName("批量 happy: 3 头 → 落 3 条 marketing + 3 次 fireEvent + 3 次 PigMarketingEvent，各头重量独立、照片逐头复制")
    void batch_happyPath() {
        when(pigMapper.selectById(501L)).thenReturn(mkPig(501L, "260520-S-001"));
        when(pigMapper.selectById(502L)).thenReturn(mkPig(502L, "260520-S-002"));
        when(pigMapper.selectById(503L)).thenReturn(mkPig(503L, "260520-S-003"));

        SlaughterBatchBo bo = mkBatchBo(
            List.of(mkItem(501L, "120.00"), mkItem(502L, "130.50"), mkItem(503L, "140.25")),
            "9001");

        List<org.dromara.djs.breed.event.slaughter.domain.vo.PigMarketingVo> vos = service.recordSlaughterBatch(bo);
        assertThat(vos).hasSize(3);

        ArgumentCaptor<PigMarketing> m = ArgumentCaptor.forClass(PigMarketing.class);
        verify(marketingMapper, times(3)).insert(m.capture());
        assertThat(m.getAllValues()).extracting(PigMarketing::getPigId)
            .containsExactly(501L, 502L, 503L);
        // 每头重量独立（不是整批同一值）
        assertThat(m.getAllValues()).extracting(x -> x.getOutWeight().toPlainString())
            .containsExactly("120.00", "130.50", "140.25");
        // 甲方口径：照片逐头复制——3 行各自落一份同样的 oss_ids
        assertThat(m.getAllValues()).extracting(PigMarketing::getOssIds)
            .containsExactly("9001", "9001", "9001");
        // 共用录入项逐头落库
        assertThat(m.getAllValues()).allSatisfy(x -> {
            assertThat(x.getOutDest()).isEqualTo("slaughter");
            assertThat(x.getOperator()).isEqualTo("2061591133665759233");
            assertThat(x.getMarketingDate()).isEqualTo(LocalDateTime.of(2026, 8, 17, 9, 0));
        });

        // 状态机 3 次 SLAUGHTER
        ArgumentCaptor<PigEventBo> ev = ArgumentCaptor.forClass(PigEventBo.class);
        verify(pigCoreService, times(3)).fireEvent(ev.capture());
        assertThat(ev.getAllValues()).allSatisfy(e ->
            assertThat(e.getEventType()).isEqualTo(PigStatusEvent.SLAUGHTER));

        // 燎毛白条来源事件 3 次（少一次 = 燎毛间少一条白条）
        verify(eventPublisher, times(3)).publishEvent(any(PigMarketingEvent.class));
    }

    @Test
    @DisplayName("批量回滚: 第 2 头 fireEvent 抛错 → 异常透传，第 3 头不再 INSERT（整批同事务全回滚）")
    void batch_rollbackOnFailure() {
        when(pigMapper.selectById(501L)).thenReturn(mkPig(501L, "260520-S-001"));
        when(pigMapper.selectById(502L)).thenReturn(mkPig(502L, "260520-S-002"));
        when(pigMapper.selectById(503L)).thenReturn(mkPig(503L, "260520-S-003"));
        // 第 2 头触发状态机失败（如已是 END 终态）
        when(pigCoreService.fireEvent(any())).thenAnswer(inv -> {
            PigEventBo e = inv.getArgument(0);
            if (Long.valueOf(502L).equals(e.getPigId())) {
                throw new ServiceException("pig.state.terminal");
            }
            return null;
        });

        SlaughterBatchBo bo = mkBatchBo(
            List.of(mkItem(501L, "120.00"), mkItem(502L, "130.50"), mkItem(503L, "140.25")),
            "9001");

        assertThatThrownBy(() -> service.recordSlaughterBatch(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.state.terminal");
        // 循环在第 2 头中断：只走到 2 次 insert，第 3 头未执行；事务回滚后一条不落
        verify(marketingMapper, times(2)).insert(any(PigMarketing.class));
        verify(eventPublisher, times(1)).publishEvent(any(PigMarketingEvent.class));
    }

    @Test
    @DisplayName("批量校验: items 空 / 照片空 → ServiceException，一条不落")
    void batch_validate() {
        SlaughterBatchBo empty = mkBatchBo(List.of(), "9001");
        assertThatThrownBy(() -> service.recordSlaughterBatch(empty))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("slaughter.batch.empty");

        SlaughterBatchBo noPhoto = mkBatchBo(List.of(mkItem(501L, "120.00")), "");
        assertThatThrownBy(() -> service.recordSlaughterBatch(noPhoto))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("slaughter.photo");

        verify(marketingMapper, never()).insert(any(PigMarketing.class));
        verify(eventPublisher, never()).publishEvent(any(PigMarketingEvent.class));
    }

    @Test
    @DisplayName("batch-pigs: pigId → 耳号，按入参顺序返回、去重、查不到的跳过")
    void listBatchPigs_orderAndDedup() {
        when(pigMapper.selectByIds(anyCollection()))
            .thenReturn(List.of(mkPig(502L, "260520-S-002"), mkPig(501L, "260520-S-001")));

        // 入参顺序 501,502,501(重复),999(不存在)
        List<SlaughterBatchPigVo> vos = service.listBatchPigs(List.of(501L, 502L, 501L, 999L));

        assertThat(vos).extracting(SlaughterBatchPigVo::getPigId).containsExactly(501L, 502L);
        assertThat(vos).extracting(SlaughterBatchPigVo::getEarNo)
            .containsExactly("260520-S-001", "260520-S-002");
        assertThat(service.listBatchPigs(List.of())).isEmpty();
    }

    @Test
    @DisplayName("非法 transition: END → fireEvent 抛 terminal 透传，publish 不调")
    void invalidTransition_terminal() {
        Pig pig = mkFatteningPig();
        pig.setCurrentStatus(PigLifecycle.END.name());
        when(pigMapper.selectById(500L)).thenReturn(pig);
        when(pigCoreService.fireEvent(any()))
            .thenThrow(new ServiceException("pig.state.terminal"));

        SlaughterBo bo = mkBo(500L, "5001", new BigDecimal("125.50"));
        assertThatThrownBy(() -> service.recordSlaughter(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("pig.state.terminal");
        // fireEvent 抛了，publish 不应该被调（事务回滚后）
        verify(eventPublisher, never()).publishEvent(any(PigMarketingEvent.class));
    }
}
