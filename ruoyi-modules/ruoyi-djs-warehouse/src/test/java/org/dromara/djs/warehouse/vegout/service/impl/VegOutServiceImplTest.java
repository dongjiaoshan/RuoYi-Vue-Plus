package org.dromara.djs.warehouse.vegout.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.UserService;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.dromara.djs.warehouse.stock.service.ILocationStockService;
import org.dromara.djs.warehouse.veg.domain.FeedLog;
import org.dromara.djs.warehouse.veg.domain.HandleRecord;
import org.dromara.djs.warehouse.veg.domain.VegetableHandle;
import org.dromara.djs.warehouse.veg.mapper.FeedLogMapper;
import org.dromara.djs.warehouse.veg.mapper.VegetableHandleMapper;
import org.dromara.djs.warehouse.vegout.domain.bo.VegOutItemBo;
import org.dromara.djs.warehouse.vegout.domain.bo.VegOutSubmitBo;
import org.dromara.djs.warehouse.vegout.domain.query.VegOutQuery;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutBatchVo;
import org.dromara.djs.warehouse.vegout.domain.vo.VegOutDetailVo;
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

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VegOutServiceImpl} 单元测试（admin row185 产品内部处理 / row187 毛菜间批量出库）。
 *
 * <p>重点锁三件事：① 果蔬月台去向在毛菜处理行缺失时<b>必须抛异常阻断</b>
 * （否则扣了库存、货却永远到不了月台 —— clean-QA 实测复现过的 P0 静默丢货）；
 * ② 两条去向都<b>只写流水台账（handle_record / feed_log），绝不碰 {@code vegetable_handle} 的重量桶</b>
 * —— 本功能出的是已入库库存，那份重量已计进 handled/stock_in，再记一次就是同一 kg 进两个互斥桶：
 * feed_weight 让地块卡「剩余 = 采摘 − 处理 − 饲料」显负、send_platform_weight 让 recomputeLoss 被静默钳零；
 * ③ 库位/业态前置校验真的拦得住。</p>
 *
 * @author djs
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VegOutServiceImpl 单元测试 (row185/row187)")
class VegOutServiceImplTest {

    private static final Long FRESH_VEG_LOC = 6L;

    @Mock
    private org.dromara.djs.warehouse.vegout.mapper.VegOutMapper vegOutMapper;
    @Mock
    private LocationStockMapper locationStockMapper;
    @Mock
    private LocationInfoMapper locationInfoMapper;
    @Mock
    private ProductInfoMapper productInfoMapper;
    @Mock
    private StockFlowMapper stockFlowMapper;
    @Mock
    private CropInfoMapper cropInfoMapper;
    @Mock
    private VegetableHandleMapper vegetableHandleMapper;
    @Mock
    private FeedLogMapper feedLogMapper;
    @Mock
    private org.dromara.djs.warehouse.veg.mapper.HandleRecordMapper handleRecordMapper;
    @Mock
    private ILocationStockService locationStockService;
    @Mock
    private org.dromara.djs.common.encoder.IBizCodeGenerator bizCodeGenerator;
    @Mock
    private UserService userService;

    private VegOutServiceImpl service;

    /** MP 单测 entity cache 预热（见 skill coder-mp-entity-cache-test）。 */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, LocationInfo.class);
        TableInfoHelper.initTableInfo(assistant, CropInfo.class);
        TableInfoHelper.initTableInfo(assistant, VegetableHandle.class);
    }

    @BeforeEach
    void setup() {
        service = new VegOutServiceImpl(vegOutMapper, locationStockMapper, locationInfoMapper, productInfoMapper,
            stockFlowMapper, cropInfoMapper, vegetableHandleMapper, feedLogMapper, handleRecordMapper,
            locationStockService, bizCodeGenerator, userService);
        LocationInfo loc = new LocationInfo();
        loc.setId(FRESH_VEG_LOC);
        loc.setLocationCode("L0006");
        // row194 起可出库库位扩到三个（毛菜鲜品库/干货库/蛋类库），service 改用 selectList 批量解析
        when(locationInfoMapper.selectList(any())).thenReturn(java.util.List.of(loc));
        // 单号改走统一编码生成器（7 位纯数字）
        when(bizCodeGenerator.generate(any(), any())).thenReturn("0000001");
        when(locationStockService.productOut(any())).thenReturn(999L);
    }

    private LocationStock mkStock(Long id, Long productId, Long plotId) {
        LocationStock s = new LocationStock();
        s.setId(id);
        s.setLocationId(FRESH_VEG_LOC);
        s.setProductId(productId);
        s.setPlotId(plotId);
        s.setProductStock(new BigDecimal("100.000"));
        return s;
    }

    private ProductInfo mkVegProduct(Long id) {
        ProductInfo p = new ProductInfo();
        p.setId(id);
        p.setProductName("上海青");
        p.setBelongType("vegetable");
        return p;
    }

    private VegOutSubmitBo mkBo(String dest, Long stockId, String qty) {
        VegOutItemBo item = new VegOutItemBo();
        item.setStockId(stockId);
        item.setQuantity(new BigDecimal(qty));
        VegOutSubmitBo bo = new VegOutSubmitBo();
        bo.setOutDate(new Date());
        bo.setOutDest(dest);
        bo.setItems(List.of(item));
        return bo;
    }

    private void stubHandleFound(Long cropId, Long plotId) {
        CropInfo crop = new CropInfo();
        crop.setId(cropId);
        crop.setCropName("上海青");
        when(cropInfoMapper.selectOne(any())).thenReturn(crop);
        when(cropInfoMapper.selectById(any())).thenReturn(crop);
        VegetableHandle h = new VegetableHandle();
        h.setId(77L);
        h.setCropId(cropId);
        h.setPlotId(plotId);
        h.setSendPlatformWeight(new BigDecimal("10.000"));
        h.setFeedWeight(new BigDecimal("5.000"));
        when(vegetableHandleMapper.selectOne(any())).thenReturn(h);
    }

    @Test
    @DisplayName("果蔬月台：写 handle_record 明细（月台待入库 + 日统计都读它），并回写流水的 batch_no / plot_id")
    void vegDock_writesHandleRecord() {
        when(locationStockMapper.selectById(1L)).thenReturn(mkStock(1L, 10L, 20L));
        when(productInfoMapper.selectById(10L)).thenReturn(mkVegProduct(10L));
        stubHandleFound(30L, 20L);

        String batchNo = service.submit(mkBo("veg_dock", 1L, "12.000"), true);

        // row192：单号改走统一编码生成器，7 位纯数字（旧格式 VO+时间戳+4位随机 已废弃）
        assertThat(batchNo).isEqualTo("0000001");
        // 不写有机饲喂记录
        verify(feedLogMapper, never()).insert(any(FeedLog.class));

        ArgumentCaptor<StockFlow> fc = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper).updateById(fc.capture());
        assertThat(fc.getValue().getBatchNo()).isEqualTo(batchNo);
        assertThat(fc.getValue().getPlotId()).isEqualTo(20L);

        // 月台待入库量（VegReceiveMapper#selectSelfPending，按产品拆）+ 日统计「发往月台果蔬总重」
        // 读的都是 handle_record 而非 vegetable_handle.send_platform_weight —— 这条明细是唯一的账
        ArgumentCaptor<org.dromara.djs.warehouse.veg.domain.HandleRecord> rc =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.veg.domain.HandleRecord.class);
        verify(handleRecordMapper).insert(rc.capture());
        assertThat(rc.getValue().getHandleTarget()).as("handle_target=2 月台").isEqualTo(2);
        assertThat(rc.getValue().getRecordWeight()).isEqualByComparingTo("12.000");
        assertThat(rc.getValue().getHandleId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("⚠️P0 回归：果蔬月台出的也是**已入库**库存，绝不可累加 send_platform_weight —— 否则 recomputeLoss 多减后静默钳零")
    void vegDock_mustNotTouchVegetableHandleWeights() {
        // 与饲料同一个根因，只是它不显负：损耗 = 采摘 − 入库 − 月台 − 饲料，
        // 月台那一项被重复计入后 loss 转负，recomputeLoss 会 warn + 归零，页面上看不出来。
        when(locationStockMapper.selectById(1L)).thenReturn(mkStock(1L, 10L, 20L));
        when(productInfoMapper.selectById(10L)).thenReturn(mkVegProduct(10L));
        stubHandleFound(30L, 20L);   // 既有行 send_platform_weight=10.000 —— 有得可加，才验得出「就是不加」

        service.submit(mkBo("veg_dock", 1L, "12.000"), true);

        verify(handleRecordMapper).insert(any(org.dromara.djs.warehouse.veg.domain.HandleRecord.class));
        verify(vegetableHandleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("⚠️P0：果蔬月台但缺毛菜处理行（采摘直送毛菜保鲜室的合法库存）→ 按需补建后照常累加，不能拦死也不能丢货")
    void vegDock_missingHandleRow_autoCreates() {
        when(locationStockMapper.selectById(1L)).thenReturn(mkStock(1L, 10L, 20L));
        when(productInfoMapper.selectById(10L)).thenReturn(mkVegProduct(10L));
        CropInfo crop = new CropInfo();
        crop.setId(30L);
        crop.setCropName("上海青");
        when(cropInfoMapper.selectOne(any())).thenReturn(crop);
        when(cropInfoMapper.selectById(any())).thenReturn(crop);
        // 查不到既有 handle 行 → 走补建；补建后 insert 会回填 id
        when(vegetableHandleMapper.selectOne(any())).thenReturn(null);
        when(vegetableHandleMapper.insert(any(VegetableHandle.class))).thenAnswer(inv -> {
            inv.getArgument(0, VegetableHandle.class).setId(88L);
            return 1;
        });

        service.submit(mkBo("veg_dock", 1L, "12.000"), true);

        ArgumentCaptor<VegetableHandle> created = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(vegetableHandleMapper).insert(created.capture());
        assertThat(created.getValue().getCropId()).isEqualTo(30L);
        assertThat(created.getValue().getPlotId()).isEqualTo(20L);
        // 补建行各重量记 0（这批货没走过毛菜处理流程），且补建之后也不再改任何重量列
        assertThat(created.getValue().getPickedWeight()).isEqualByComparingTo("0");
        assertThat(created.getValue().getStockInWeight()).isEqualByComparingTo("0");
        verify(vegetableHandleMapper, never()).updateById(any(VegetableHandle.class));

        // 补建的意义就是给明细提供挂载点 —— 明细必须真的挂上去
        ArgumentCaptor<org.dromara.djs.warehouse.veg.domain.HandleRecord> rc =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.veg.domain.HandleRecord.class);
        verify(handleRecordMapper).insert(rc.capture());
        assertThat(rc.getValue().getHandleId()).as("挂在补建出来的那行上").isEqualTo(88L);
        assertThat(rc.getValue().getRecordWeight()).isEqualByComparingTo("12.000");
    }

    @Test
    @DisplayName("⚠️P0：库存行无地块 → 归属定不了，果蔬月台必须拦（补建也没有 plot 可挂）")
    void vegDock_nullPlot_mustBlock() {
        when(locationStockMapper.selectById(1L)).thenReturn(mkStock(1L, 10L, null));
        when(productInfoMapper.selectById(10L)).thenReturn(mkVegProduct(10L));

        assertThatThrownBy(() -> service.submit(mkBo("veg_dock", 1L, "12.000"), true))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("未关联地块");
        // 拦住时不得留下补建行
        verify(vegetableHandleMapper, never()).insert(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("⚠️P0：产品未配关联作物 → 果蔬月台拦，提示指向作物管理")
    void vegDock_noCropMapping_mustBlock() {
        when(locationStockMapper.selectById(1L)).thenReturn(mkStock(1L, 10L, 20L));
        when(productInfoMapper.selectById(10L)).thenReturn(mkVegProduct(10L));
        when(cropInfoMapper.selectOne(any())).thenReturn(null);   // 反查不到作物

        assertThatThrownBy(() -> service.submit(mkBo("veg_dock", 1L, "12.000"), true))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("未配置对应作物");
        verify(vegetableHandleMapper, never()).insert(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("饲料饲喂：只写有机饲喂记录 feed_type=veg_handle（位置=毛菜间）")
    void feed_writesFeedLog() {
        when(locationStockMapper.selectById(1L)).thenReturn(mkStock(1L, 10L, 20L));
        when(productInfoMapper.selectById(10L)).thenReturn(mkVegProduct(10L));
        stubHandleFound(30L, 20L);

        service.submit(mkBo("feed", 1L, "8.000"), false);

        ArgumentCaptor<FeedLog> fc = ArgumentCaptor.forClass(FeedLog.class);
        verify(feedLogMapper).insert(fc.capture());
        assertThat(fc.getValue().getFeedType()).isEqualTo("veg_handle");
        assertThat(fc.getValue().getFeedWeight()).isEqualByComparingTo("8.000");
        assertThat(fc.getValue().getLocationId()).isEqualTo(FRESH_VEG_LOC);

        // 饲料去向不进「发往月台」日统计
        verify(handleRecordMapper, never()).insert(any(org.dromara.djs.warehouse.veg.domain.HandleRecord.class));
    }

    @Test
    @DisplayName("⚠️P0 回归：饲料饲喂出的是**已入库**库存，绝不可累加 feed_weight —— 否则地块剩余被二次扣减成负数")
    void feed_mustNotTouchVegetableHandleWeights() {
        // 生产实测 B-连6-2-001：采摘 340 / 处理入库 283，再从库存领 70kg 去饲喂，
        // 累加 feed_weight 后地块卡「剩余 = 采摘 − 处理 − 饲料」= 340−283−70 = −13kg。
        when(locationStockMapper.selectById(1L)).thenReturn(mkStock(1L, 10L, 20L));
        when(productInfoMapper.selectById(10L)).thenReturn(mkVegProduct(10L));
        stubHandleFound(30L, 20L);   // 毛菜处理行存在且 feed_weight=5.000 —— 有得可加，才验得出「就是不加」

        service.submit(mkBo("feed", 1L, "70.000"), false);

        verify(feedLogMapper).insert(any(FeedLog.class));
        // 三道：不改、不补建、连查都不查（改动一旦被还原，这三条任一都会红）
        verify(vegetableHandleMapper, never()).updateById(any(VegetableHandle.class));
        verify(vegetableHandleMapper, never()).insert(any(VegetableHandle.class));
        verify(vegetableHandleMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("饲料饲喂：库存行无地块 / 产品未配作物也照常放行（不像月台那样拦）")
    void feed_missingCropOrPlot_stillLogs() {
        when(locationStockMapper.selectById(1L)).thenReturn(mkStock(1L, 10L, null));
        when(productInfoMapper.selectById(10L)).thenReturn(mkVegProduct(10L));
        when(cropInfoMapper.selectOne(any())).thenReturn(null);

        service.submit(mkBo("feed", 1L, "8.000"), false);

        verify(feedLogMapper).insert(any(FeedLog.class));
        verify(vegetableHandleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("前置校验：非毛菜鲜品库的库存行拒绝出库")
    void rejectNonFreshVegLocation() {
        LocationStock s = mkStock(1L, 10L, 20L);
        s.setLocationId(999L);
        when(locationStockMapper.selectById(1L)).thenReturn(s);

        assertThatThrownBy(() -> service.submit(mkBo("veg_dock", 1L, "1.000"), true))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("毛菜鲜品库");
        verify(locationStockService, never()).productOut(any());
    }

    @Test
    @DisplayName("前置校验：非果蔬产品拒绝出库")
    void rejectNonVegetableProduct() {
        when(locationStockMapper.selectById(1L)).thenReturn(mkStock(1L, 10L, 20L));
        ProductInfo pork = mkVegProduct(10L);
        // row194 起业态白名单放宽到 {vegetable, dry_good, egg, other}，但猪肉仍在白名单外 → 照样拒
        pork.setBelongType("pork");
        when(productInfoMapper.selectById(10L)).thenReturn(pork);

        assertThatThrownBy(() -> service.submit(mkBo("veg_dock", 1L, "1.000"), true))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不支持毛菜间出库");
        verify(locationStockService, never()).productOut(any());
    }

    /**
     * V6 row101（甲方邓博 2026-08-13 口径，见 doc/16 §0「三期是什么」）：三期货不走果蔬月台。
     *
     * <p>用<b>带真实 plotId 的三期库存行</b>做用例：多地块三期货 plot_id 为空、本来就会被下游
     * {@code resolveHandleForPlatform} 拦下，拦不住的正是「恰好 1 块在种地块」这条 —— 它整条月台链路
     * 走得通，而月台收货只写普通篮（{@code addStockByPlotLocation} 硬带 {@code third_phase=0}），
     * 货一进蔬菜保鲜库就洗成普通有机货、可被门店取走，静默违反「三期不发到门店」。
     * 所以守卫必须按 {@code third_phase} 判，不能按「有没有地块」判。</p>
     */
    @Test
    @DisplayName("row101：三期货出库到果蔬月台 → 拒绝（且必须在扣库存前拦住，不能扣了再回滚）")
    void rejectThirdPhaseToVegDock() {
        LocationStock s = mkStock(1L, 10L, 20L);
        s.setThirdPhase(1);
        when(locationStockMapper.selectById(1L)).thenReturn(s);
        when(productInfoMapper.selectById(10L)).thenReturn(mkVegProduct(10L));

        assertThatThrownBy(() -> service.submit(mkBo("veg_dock", 1L, "1.000"), true))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("是三期货，不走果蔬月台");
        verify(locationStockService, never()).productOut(any());
        verify(handleRecordMapper, never()).insert(any(HandleRecord.class));
    }

    @Test
    @DisplayName("row101：三期货出库到其它去向（饲料饲喂）→ 放行（甲方只禁月台，出库记账照常）")
    void allowThirdPhaseToNonDockDest() {
        LocationStock s = mkStock(1L, 10L, 20L);
        s.setThirdPhase(1);
        when(locationStockMapper.selectById(1L)).thenReturn(s);
        when(productInfoMapper.selectById(10L)).thenReturn(mkVegProduct(10L));
        stubHandleFound(30L, 20L);

        service.submit(mkBo("feed", 1L, "8.000"), false);

        verify(locationStockService).productOut(any());
        verify(feedLogMapper).insert(any(FeedLog.class));
    }

    @Test
    @DisplayName("row194：干货 / 蛋类 / 其他业态可出库（白名单放宽后不再被业态守卫拦）")
    void allowDryGoodAndEggProduct() {
        for (String belongType : new String[] {"dry_good", "egg", "other"}) {
            reset(locationStockService);
            when(locationStockMapper.selectById(1L)).thenReturn(mkStock(1L, 10L, 20L));
            ProductInfo p = mkVegProduct(10L);
            p.setBelongType(belongType);
            when(productInfoMapper.selectById(10L)).thenReturn(p);
            when(locationStockService.productOut(any())).thenReturn(99L);

            service.submit(mkBo("kitchen", 1L, "1.000"), true);

            verify(locationStockService).productOut(any());
            // D3：非果蔬不累加毛菜处理送月台重量（那是果蔬专属报表，混入会污染）
            verify(vegetableHandleMapper, never()).updateById(any(VegetableHandle.class));
        }
    }

    @Test
    @DisplayName("V6 row31 导出：走不分页的 selectBatchList 并回填操作人姓名（口径与列表一致）")
    void exportList_usesUnpagedQueryAndFillsOperatorName() {
        VegOutBatchVo row = new VegOutBatchVo();
        row.setBatchNo("0000006");
        row.setOperatorId(7L);
        row.setTotalWeight(new BigDecimal("12.000"));
        row.setTotalAmount(new BigDecimal("120.00"));
        Date begin = new Date(0L);
        Date end = new Date();
        when(vegOutMapper.selectBatchList(begin, end, "kitchen", 7L)).thenReturn(new java.util.ArrayList<>(List.of(row)));
        when(userService.selectNicknameById(7L)).thenReturn("张三");

        VegOutQuery q = new VegOutQuery();
        q.setBeginDate(begin);
        q.setEndDate(end);
        q.setOutDest("kitchen");
        q.setOperatorId(7L);
        List<VegOutBatchVo> rows = service.queryBatchList(q);

        assertThat(rows).hasSize(1);
        // 导出必须带出姓名而不是裸 id —— 列表页显示的就是姓名，甲方拿导出对账
        assertThat(rows.get(0).getOperatorName()).isEqualTo("张三");
        assertThat(rows.get(0).getBatchNo()).isEqualTo("0000006");
        // 不得退化成「分页查一个够大的 pageSize」
        verify(vegOutMapper).selectBatchList(begin, end, "kitchen", 7L);
        verify(vegOutMapper, never()).selectBatchPage(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("V6 row30 导出：出库量按单位派生带单位串（kg 三位小数 / 计件去尾零），与详情弹框显示一致")
    void exportDetail_derivesQtyLabelPerUnit() {
        when(vegOutMapper.selectBatchDetail("0000006", "青")).thenReturn(new java.util.ArrayList<>(List.of(
            mkDetail("上海青", "kg", "12"),
            mkDetail("大米", "袋", "3.000"),
            mkDetail("土鸡蛋", null, "0.5"))));

        List<VegOutDetailVo> rows = service.queryBatchDetailForExport("0000006", "青");

        assertThat(rows).extracting(VegOutDetailVo::getOutQtyLabel)
            // 单位缺失按 kg 处理，否则 0.5kg 会印成没有单位的裸 0.5
            .containsExactly("12.000kg", "3 袋", "0.500kg");
    }

    @Test
    @DisplayName("V6 row30 导出：单号为空直接返空，不打 mapper（防前端裸调）")
    void exportDetail_blankBatchNo_returnsEmpty() {
        assertThat(service.queryBatchDetailForExport("  ", null)).isEmpty();
        verify(vegOutMapper, never()).selectBatchDetail(any(), any());
    }

    private VegOutDetailVo mkDetail(String name, String unit, String qty) {
        VegOutDetailVo d = new VegOutDetailVo();
        d.setProductName(name);
        d.setProductUnit(unit);
        d.setOutWeight(new BigDecimal(qty));
        return d;
    }

    @Test
    @DisplayName("单条内部处理（asBatch=false）不生成出库单号，不进 row187 列表")
    void singleInternalHandle_noBatchNo() {
        when(locationStockMapper.selectById(1L)).thenReturn(mkStock(1L, 10L, 20L));
        when(productInfoMapper.selectById(10L)).thenReturn(mkVegProduct(10L));
        stubHandleFound(30L, 20L);

        assertThat(service.submit(mkBo("veg_dock", 1L, "5.000"), false)).isNull();
        ArgumentCaptor<StockFlow> fc = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper).updateById(fc.capture());
        assertThat(fc.getValue().getBatchNo()).isNull();
    }
}
