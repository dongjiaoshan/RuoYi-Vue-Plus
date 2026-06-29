package org.dromara.djs.warehouse.trace.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.cut.domain.PigCutRecord;
import org.dromara.djs.warehouse.cut.mapper.PigCutRecordMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.trace.domain.TraceCode;
import org.dromara.djs.warehouse.trace.domain.TraceCodeTypeConst;
import org.dromara.djs.warehouse.trace.domain.TraceContentConst;
import org.dromara.djs.warehouse.trace.domain.TraceEvent;
import org.dromara.djs.warehouse.trace.mapper.TraceCodeMapper;
import org.dromara.djs.warehouse.trace.mapper.TraceEventMapper;
import org.junit.jupiter.api.BeforeAll;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TraceServiceImpl} 单测（TRC-CORE-001）。
 *
 * <h3>覆盖场景</h3>
 * <ul>
 *   <li>genCode 三业态：pork（belong_type=pork）→ code_type=pork + 填 pigEarNo；
 *       veg（belong_type=vegetable）→ code_type=veg + 填 plotId；
 *       gift（belong_type=gift_box）→ code_type=gift + gift_components NULL</li>
 *   <li>genCode 业务码占位符 productCode 传 PG/VG/GF</li>
 *   <li>recordEvent happy：写一条 immutable 流水 + trace_content 正确</li>
 *   <li>recordEvent 容错：produceCode 空 → 不 insert、不抛；insert 抛异常 → 不抛</li>
 *   <li>recordEventByEarNo 反查不到 → 不 recordEvent、不抛（猪肉链无生成入口的 V1 预期）</li>
 * </ul>
 *
 * <p>MP entity cache 预热（skill coder-mp-entity-cache-test）：{@code findProduceCodeByEarNo} 内部
 * LambdaQueryWrapper method-ref 会触发 TableInfoHelper 解析列名，必须先注册 TraceCode / TraceEvent /
 * ProductInfo entity。genCode 路径用 spy stub protected insertTraceCode / insertTraceEvent 避开真实
 * baseMapper insert。</p>
 *
 * @author djs
 * @since TRC-CORE-001
 */
@Tag("local")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TraceServiceImpl 单元测试")
class TraceServiceImplTest {

    @Mock
    private TraceCodeMapper traceCodeMapper;

    @Mock
    private TraceEventMapper traceEventMapper;

    @Mock
    private ProductInfoMapper productInfoMapper;

    @Mock
    private BarInfoMapper barInfoMapper;

    @Mock
    private PigCutRecordMapper pigCutRecordMapper;

    @Mock
    private IBizCodeGenerator bizCodeGenerator;

    private TraceServiceImpl service;

    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, TraceCode.class);
        TableInfoHelper.initTableInfo(assistant, TraceEvent.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, BarInfo.class);
        TableInfoHelper.initTableInfo(assistant, PigCutRecord.class);
    }

    @BeforeEach
    void setUp() {
        // spy 以便 stub protected insertTraceCode / insertTraceEvent / findBarByEarNo（避开真实 baseMapper.insert）
        service = spy(new TraceServiceImpl(
            traceCodeMapper, traceEventMapper, productInfoMapper, barInfoMapper,
            pigCutRecordMapper, bizCodeGenerator));
    }

    private ProductInfo product(Long id, String belongType) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setBelongType(belongType);
        p.setProductName("测试产品");
        return p;
    }

    @Test
    @DisplayName("genCode pork：belong_type=pork → code_type=pork + 填 pigEarNo，业务码占位 PG")
    void genCode_pork_setsCodeTypePorkAndEarNo() {
        when(productInfoMapper.selectById(1001L)).thenReturn(product(1001L, "pork"));
        when(bizCodeGenerator.generate(eq(BizCodeType.TRACE_CODE), anyMap())).thenReturn("T20260615PG000001");
        doNothing().when(service).insertTraceCode(any(TraceCode.class));
        // 本测专注 trace_code 装配；耳号事件回填另有专测 → 此处 stub bar 反查为空，跳过回填
        doReturn(null).when(service).findBarByEarNo("01A12605001");

        String code = service.genCode(1001L, "01A12605001", null, null);

        assertThat(code).isEqualTo("T20260615PG000001");
        ArgumentCaptor<TraceCode> captor = ArgumentCaptor.forClass(TraceCode.class);
        verify(service, times(1)).insertTraceCode(captor.capture());
        TraceCode tc = captor.getValue();
        assertThat(tc.getCodeType()).isEqualTo(TraceCodeTypeConst.PORK);
        assertThat(tc.getProductId()).isEqualTo(1001L);
        assertThat(tc.getPigEarNo()).isEqualTo("01A12605001");
        assertThat(tc.getPlotId()).isNull();
        assertThat(tc.getGiftComponents()).isNull();
        assertThat(tc.getQrOssId()).isNull();

        ArgumentCaptor<Map<String, Object>> ctxCaptor = ArgumentCaptor.forClass(Map.class);
        verify(bizCodeGenerator).generate(eq(BizCodeType.TRACE_CODE), ctxCaptor.capture());
        assertThat(ctxCaptor.getValue()).containsEntry("productCode", TraceCodeTypeConst.PRODUCT_CODE_PORK);
    }

    @Test
    @DisplayName("genCode veg：belong_type=vegetable → code_type=veg + 填 plotId，业务码占位 VG")
    void genCode_veg_setsCodeTypeVegAndPlotId() {
        when(productInfoMapper.selectById(1002L)).thenReturn(product(1002L, "vegetable"));
        when(bizCodeGenerator.generate(eq(BizCodeType.TRACE_CODE), anyMap())).thenReturn("T20260615VG000001");
        doNothing().when(service).insertTraceCode(any(TraceCode.class));

        String code = service.genCode(1002L, null, 5050L, 7001L);

        assertThat(code).isEqualTo("T20260615VG000001");
        ArgumentCaptor<TraceCode> captor = ArgumentCaptor.forClass(TraceCode.class);
        verify(service, times(1)).insertTraceCode(captor.capture());
        TraceCode tc = captor.getValue();
        assertThat(tc.getCodeType()).isEqualTo(TraceCodeTypeConst.VEG);
        assertThat(tc.getPlotId()).isEqualTo(5050L);
        assertThat(tc.getPigEarNo()).isNull();
        // 需求 C：追溯码归属门店随入参写入 trace_code.store_id
        assertThat(tc.getStoreId()).isEqualTo(7001L);
    }

    @Test
    @DisplayName("genCode gift：belong_type=gift_box → code_type=gift + gift_components NULL，业务码占位 GF")
    void genCode_gift_setsCodeTypeGift() {
        when(productInfoMapper.selectById(1003L)).thenReturn(product(1003L, "gift_box"));
        when(bizCodeGenerator.generate(eq(BizCodeType.TRACE_CODE), anyMap())).thenReturn("T20260615GF000001");
        doNothing().when(service).insertTraceCode(any(TraceCode.class));

        String code = service.genCode(1003L, null, null, null);

        assertThat(code).isEqualTo("T20260615GF000001");
        ArgumentCaptor<TraceCode> captor = ArgumentCaptor.forClass(TraceCode.class);
        verify(service, times(1)).insertTraceCode(captor.capture());
        TraceCode tc = captor.getValue();
        assertThat(tc.getCodeType()).isEqualTo(TraceCodeTypeConst.GIFT);
        assertThat(tc.getGiftComponents()).isNull();
        assertThat(tc.getPigEarNo()).isNull();
        assertThat(tc.getPlotId()).isNull();

        ArgumentCaptor<Map<String, Object>> ctxCaptor = ArgumentCaptor.forClass(Map.class);
        verify(bizCodeGenerator).generate(eq(BizCodeType.TRACE_CODE), ctxCaptor.capture());
        assertThat(ctxCaptor.getValue()).containsEntry("productCode", TraceCodeTypeConst.PRODUCT_CODE_GIFT);
    }

    @Test
    @DisplayName("recordEvent happy：写一条 immutable 流水，trace_content + trace_time 正确")
    void recordEvent_happy_insertsOneEvent() {
        doNothing().when(service).insertTraceEvent(any(TraceEvent.class));

        service.recordEvent("T20260615VG000001", TraceContentConst.IN_STOCK);

        ArgumentCaptor<TraceEvent> captor = ArgumentCaptor.forClass(TraceEvent.class);
        verify(service, times(1)).insertTraceEvent(captor.capture());
        TraceEvent ev = captor.getValue();
        assertThat(ev.getProduceCode()).isEqualTo("T20260615VG000001");
        assertThat(ev.getTraceContent()).isEqualTo(TraceContentConst.IN_STOCK);
        assertThat(ev.getTraceTime()).isNotNull();
    }

    @Test
    @DisplayName("recordEvent 容错：produceCode 空 → 不 insert、不抛")
    void recordEvent_blankProduceCode_skipsSilently() {
        assertThatCode(() -> service.recordEvent("  ", TraceContentConst.SHIP)).doesNotThrowAnyException();
        verify(service, never()).insertTraceEvent(any(TraceEvent.class));
    }

    @Test
    @DisplayName("recordEvent 容错：insert 抛异常 → swallow，不拖垮主业务")
    void recordEvent_insertThrows_swallowsException() {
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
            .when(service).insertTraceEvent(any(TraceEvent.class));

        assertThatCode(() -> service.recordEvent("T20260615VG000001", TraceContentConst.SHIP))
            .doesNotThrowAnyException();
        verify(service, times(1)).insertTraceEvent(any(TraceEvent.class));
    }

    @Test
    @DisplayName("recordEventByEarNo 反查不到：不 recordEvent、不抛（猪肉链无生成入口的 V1 预期）")
    void recordEventByEarNo_notFound_skipsSilently() {
        doReturn(null).when(service).findProduceCodeByEarNo("01A12605001");

        assertThatCode(() -> service.recordEventByEarNo("01A12605001", TraceContentConst.MARKETING))
            .doesNotThrowAnyException();
        verify(service, never()).insertTraceEvent(any(TraceEvent.class));
    }

    @Test
    @DisplayName("recordEventByEarNo 反查到：转调 recordEvent 写流水")
    void recordEventByEarNo_found_recordsEvent() {
        doReturn("T20260615PG000001").when(service).findProduceCodeByEarNo("01A12605001");
        doNothing().when(service).insertTraceEvent(any(TraceEvent.class));

        service.recordEventByEarNo("01A12605001", TraceContentConst.SINGE);

        ArgumentCaptor<TraceEvent> captor = ArgumentCaptor.forClass(TraceEvent.class);
        verify(service, times(1)).insertTraceEvent(captor.capture());
        assertThat(captor.getValue().getProduceCode()).isEqualTo("T20260615PG000001");
        assertThat(captor.getValue().getTraceContent()).isEqualTo(TraceContentConst.SINGE);
    }

    // ============================ 耳号事件回填（核心 bug 修复） ============================

    private BarInfo barWithLifecycle(String earNo) {
        BarInfo bar = new BarInfo();
        bar.setId(9001L);
        bar.setEarNo(earNo);
        bar.setMarketingTime(date(2026, 6, 1, 8, 0));   // 出栏
        bar.setInTime(date(2026, 6, 2, 9, 0));          // 燎毛入库
        bar.setOutTime(date(2026, 6, 3, 10, 0));        // 分割出库 = 排酸完成
        return bar;
    }

    private static java.util.Date date(int y, int mo, int d, int h, int mi) {
        return java.util.Date.from(java.time.LocalDateTime.of(y, mo, d, h, mi)
            .atZone(java.time.ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("genCode pork：出生时按耳号回填 6 上游事件（marketing/singe/white_bar_in/white_bar_pick/slaughter/acid，真实时间戳）")
    void genCode_pork_backfillsSixEarNoEvents() {
        when(productInfoMapper.selectById(1001L)).thenReturn(product(1001L, "pork"));
        when(bizCodeGenerator.generate(eq(BizCodeType.TRACE_CODE), anyMap())).thenReturn("T20260615PG000001");
        doNothing().when(service).insertTraceCode(any(TraceCode.class));
        doNothing().when(service).insertTraceEvent(any(TraceEvent.class));
        // bar_info 沉淀全生命周期时间戳；尚无任何事件 → 6 个全回填
        doReturn(barWithLifecycle("01A12605001")).when(service).findBarByEarNo("01A12605001");
        // 白条领用时刻在 cut_record（按 white_bar_id=9001 反查）
        doReturn(date(2026, 6, 3, 7, 0)).when(service).findPickupTime(9001L);
        doReturn(java.util.Collections.<String>emptySet())
            .when(service).findExistingContents(eq("T20260615PG000001"), any());

        service.genCode(1001L, "01A12605001", null, null);

        ArgumentCaptor<TraceEvent> captor = ArgumentCaptor.forClass(TraceEvent.class);
        verify(service, times(6)).insertTraceEvent(captor.capture());
        List<TraceEvent> events = captor.getAllValues();
        // 6 事件 trace_content 齐全（含白条入库 / 白条出库领用）
        assertThat(events).extracting(TraceEvent::getTraceContent)
            .containsExactly(TraceContentConst.MARKETING, TraceContentConst.SINGE,
                TraceContentConst.WHITE_BAR_IN, TraceContentConst.WHITE_BAR_PICK,
                TraceContentConst.SLAUGHTER, TraceContentConst.ACID);
        // 全部挂在新生成的 pork 码上
        assertThat(events).allMatch(e -> "T20260615PG000001".equals(e.getProduceCode()));
        // 用真实时间戳（非 now）：marketing=6/1 8:00，singe / white_bar_in=6/2 9:00（同入库时点），
        // white_bar_pick=6/3 7:00（领用时刻，cut_record），slaughter/acid=6/3 10:00（出库 / 排酸完成）
        assertThat(events.get(0).getTraceTime()).isEqualTo(java.time.LocalDateTime.of(2026, 6, 1, 8, 0));
        assertThat(events.get(1).getTraceTime()).isEqualTo(java.time.LocalDateTime.of(2026, 6, 2, 9, 0));
        assertThat(events.get(2).getTraceTime()).isEqualTo(java.time.LocalDateTime.of(2026, 6, 2, 9, 0));
        assertThat(events.get(3).getTraceTime()).isEqualTo(java.time.LocalDateTime.of(2026, 6, 3, 7, 0));
        assertThat(events.get(4).getTraceTime()).isEqualTo(java.time.LocalDateTime.of(2026, 6, 3, 10, 0));
        assertThat(events.get(5).getTraceTime()).isEqualTo(java.time.LocalDateTime.of(2026, 6, 3, 10, 0));
    }

    @Test
    @DisplayName("回填幂等：已存在的事件不重复写（仅补缺失的）")
    void backfill_idempotent_skipsExisting() {
        doNothing().when(service).insertTraceEvent(any(TraceEvent.class));
        doReturn(barWithLifecycle("01A12605001")).when(service).findBarByEarNo("01A12605001");
        doReturn(date(2026, 6, 3, 7, 0)).when(service).findPickupTime(9001L);
        // marketing + singe 已存在 → 只补 white_bar_in / white_bar_pick / slaughter / acid
        doReturn(java.util.Set.of(TraceContentConst.MARKETING, TraceContentConst.SINGE))
            .when(service).findExistingContents(eq("T20260615PG000001"), any());

        service.backfillEarNoEvents("T20260615PG000001", "01A12605001");

        ArgumentCaptor<TraceEvent> captor = ArgumentCaptor.forClass(TraceEvent.class);
        verify(service, times(4)).insertTraceEvent(captor.capture());
        assertThat(captor.getAllValues()).extracting(TraceEvent::getTraceContent)
            .containsExactly(TraceContentConst.WHITE_BAR_IN, TraceContentConst.WHITE_BAR_PICK,
                TraceContentConst.SLAUGHTER, TraceContentConst.ACID);
    }

    @Test
    @DisplayName("回填容错：某阶段时间戳为 NULL（工序未走到）→ 该事件跳过，不造假数据")
    void backfill_nullTimestamp_skipsThatEvent() {
        doNothing().when(service).insertTraceEvent(any(TraceEvent.class));
        BarInfo bar = barWithLifecycle("01A12605001");
        bar.setOutTime(null); // 尚未分割 → slaughter/acid 无时间戳
        doReturn(bar).when(service).findBarByEarNo("01A12605001");
        // 尚未领用 → cut_record 无 pickup_time → white_bar_pick 跳过
        doReturn(null).when(service).findPickupTime(9001L);
        doReturn(java.util.Collections.<String>emptySet())
            .when(service).findExistingContents(eq("T20260615PG000001"), any());

        service.backfillEarNoEvents("T20260615PG000001", "01A12605001");

        // in_time 在（marketing/singe/white_bar_in 写）；out_time + pickup_time 缺（slaughter/acid/white_bar_pick 跳过）
        ArgumentCaptor<TraceEvent> captor = ArgumentCaptor.forClass(TraceEvent.class);
        verify(service, times(3)).insertTraceEvent(captor.capture());
        assertThat(captor.getAllValues()).extracting(TraceEvent::getTraceContent)
            .containsExactly(TraceContentConst.MARKETING, TraceContentConst.SINGE,
                TraceContentConst.WHITE_BAR_IN);
    }

    @Test
    @DisplayName("回填容错：bar_info 查不到（外购无耳号）→ 不写、不抛")
    void backfill_noBar_skipsSilently() {
        doReturn(null).when(service).findBarByEarNo("NOSUCH");

        assertThatCode(() -> service.backfillEarNoEvents("T20260615PG000001", "NOSUCH"))
            .doesNotThrowAnyException();
        verify(service, never()).insertTraceEvent(any(TraceEvent.class));
    }

    @Test
    @DisplayName("回填容错：insertTraceEvent 抛异常 → swallow，不拖垮打包主事务")
    void backfill_insertThrows_swallowsException() {
        doReturn(barWithLifecycle("01A12605001")).when(service).findBarByEarNo("01A12605001");
        doReturn(java.util.Collections.<String>emptySet())
            .when(service).findExistingContents(eq("T20260615PG000001"), any());
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
            .when(service).insertTraceEvent(any(TraceEvent.class));

        assertThatCode(() -> service.backfillEarNoEvents("T20260615PG000001", "01A12605001"))
            .doesNotThrowAnyException();
    }
}
