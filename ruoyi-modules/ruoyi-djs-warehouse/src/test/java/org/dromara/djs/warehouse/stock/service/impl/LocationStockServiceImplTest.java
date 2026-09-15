package org.dromara.djs.warehouse.stock.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.plant.plot.domain.PlotInfo;
import org.dromara.djs.plant.plot.mapper.PlotInfoMapper;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.domain.bo.LocationStockBo;
import org.dromara.djs.warehouse.stock.domain.bo.StockOutBo;
import org.dromara.djs.warehouse.stock.domain.query.LocationStockQuery;
import org.dromara.djs.warehouse.stock.domain.vo.LocationStockVo;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.dromara.common.satoken.utils.LoginHelper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LocationStockServiceImpl} 单测（WMS-MD-001）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>queryPageList happy path：JOIN 回填 locationName 不为 null</li>
 *   <li>insertByBo happy：operatorId 走 LoginHelper.getUserId() 注入（ADR-0007）</li>
 *   <li>insertByBo error：四选一规则违反（productId + earNo 同时填）→ 抛 stock.dimension.exclusive</li>
 *   <li>insertByBo error：四选一全空 → 抛 stock.dimension.exclusive</li>
 * </ul>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LocationStockServiceImpl 单元测试")
class LocationStockServiceImplTest {

    @Mock
    private LocationStockMapper stockMapper;

    @Mock
    private LocationInfoMapper locationInfoMapper;

    @Mock
    private PlotInfoMapper plotInfoMapper;

    @Mock
    private ProductInfoMapper productInfoMapper;

    @Mock
    private StockFlowMapper stockFlowMapper;

    @Mock
    private IBizCodeGenerator bizCodeGenerator;

    @Mock
    private IStockCheckService stockCheckService;

    private TestableLocationStockServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    static class TestableLocationStockServiceImpl extends LocationStockServiceImpl {
        TestableLocationStockServiceImpl(LocationStockMapper baseMapper, LocationInfoMapper locInfoMapper, PlotInfoMapper plotInfoMapper,
                                         ProductInfoMapper productInfoMapper, StockFlowMapper stockFlowMapper,
                                         IBizCodeGenerator bizCodeGenerator, IStockCheckService stockCheckService) {
            super(baseMapper, locInfoMapper, plotInfoMapper, productInfoMapper, stockFlowMapper, bizCodeGenerator, stockCheckService);
        }

        @Override
        protected LocationStock toEntity(LocationStockBo bo) {
            if (bo == null) return null;
            LocationStock e = new LocationStock();
            e.setId(bo.getId());
            e.setLocationId(bo.getLocationId());
            e.setProductId(bo.getProductId());
            e.setEarNo(bo.getEarNo());
            e.setPlotId(bo.getPlotId());
            e.setProductName(bo.getProductName());
            e.setProductStock(bo.getProductStock());
            e.setProductUnit(bo.getProductUnit());
            e.setIsEnd(bo.getIsEnd());
            e.setRemark(bo.getRemark());
            return e;
        }
    }

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            LocationStock.class);
        service = new TestableLocationStockServiceImpl(stockMapper, locationInfoMapper, plotInfoMapper,
            productInfoMapper, stockFlowMapper, bizCodeGenerator, stockCheckService);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(10086L);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private LocationStockBo sampleBo() {
        LocationStockBo bo = new LocationStockBo();
        bo.setLocationId(90001L);
        bo.setProductId(50001L);
        bo.setProductName("猪后腿肉");
        bo.setProductStock(new BigDecimal("12.500"));
        bo.setProductUnit("kg");
        return bo;
    }

    @Test
    @DisplayName("queryPageList: happy → JOIN 回填 locationName")
    void testQueryPageList_FillLocationName() {
        LocationStockQuery query = new LocationStockQuery();
        query.setLocationId(90001L);
        PageQuery pageQuery = new PageQuery(10, 1);   // PageQuery(pageSize, pageNum)

        LocationStockVo vo = new LocationStockVo();
        vo.setId(80001L);
        vo.setLocationId(90001L);
        vo.setProductName("猪后腿肉");
        // row223 / D-0068 起列表先查全量再合并分组、在内存里分页，不再走 selectVoPage
        when(stockMapper.selectVoList(any(Wrapper.class))).thenReturn(List.of(vo));

        LocationInfo loc = new LocationInfo();
        loc.setId(90001L);
        loc.setLocationName("冻品库");
        when(locationInfoMapper.selectList(any())).thenReturn(List.of(loc));

        TableDataInfo<LocationStockVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getLocationName()).as("应回填 locationName").isEqualTo("冻品库");
    }

    @Test
    @DisplayName("queryPageList: 零库存仅保留上海当天同一库存篮有流水的记录")
    void testQueryPageList_ZeroStockVisibilityUsesTodayFlowAndAllDimensions() {
        LocationStockQuery query = new LocationStockQuery();
        PageQuery pageQuery = new PageQuery(10, 1);   // PageQuery(pageSize, pageNum)
        when(stockMapper.selectVoList(any(Wrapper.class))).thenReturn(List.of());

        service.queryPageList(query, pageQuery);

        ArgumentCaptor<Wrapper<LocationStock>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(stockMapper).selectVoList(captor.capture());
        String sql = captor.getValue().getCustomSqlSegment();
        assertThat(sql)
            .contains("product_stock > 0 OR EXISTS")
            .contains("t_warehouse_stock_flow")
            .contains("warehouse_id <=> t_warehouse_location_stock.location_id")
            .contains("ear_no <=> t_warehouse_location_stock.ear_no")
            .contains("white_bar_no <=> t_warehouse_location_stock.white_bar_no")
            .contains("plot_id <=> t_warehouse_location_stock.plot_id")
            .contains("UTC_TIMESTAMP()");
    }

    @Test
    @DisplayName("queryPageList: happy → JOIN 地块表回填 blockNo（地块编号 = plot_code）")
    void testQueryPageList_FillBlockNo() {
        LocationStockQuery query = new LocationStockQuery();
        PageQuery pageQuery = new PageQuery(10, 1);   // PageQuery(pageSize, pageNum)

        LocationStockVo vo = new LocationStockVo();
        vo.setId(80002L);
        vo.setLocationId(90001L);
        vo.setPlotId(70001L);
        vo.setProductName("小白菜");
        when(stockMapper.selectVoList(any(Wrapper.class))).thenReturn(List.of(vo));
        when(locationInfoMapper.selectList(any())).thenReturn(List.of());

        PlotInfo plot = new PlotInfo();
        plot.setId(70001L);
        plot.setPlotCode("DK-001");
        when(plotInfoMapper.selectList(any())).thenReturn(List.of(plot));

        TableDataInfo<LocationStockVo> result = service.queryPageList(query, pageQuery);

        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getBlockNo()).as("应回填地块编号").isEqualTo("DK-001");
    }

    @Test
    @DisplayName("queryList: 整批都没有真实地块时，plotLabel 仍必须逐行填上（三期篮 plot_id 恒 NULL —— 早退会让导出「地块」列整列空白）")
    void testQueryList_PlotLabelFilledWhenNoRowHasPlot() {
        LocationStockVo third = new LocationStockVo();
        third.setId(90001L);
        third.setThirdPhase(1);
        LocationStockVo plain = new LocationStockVo();
        plain.setId(90002L);
        plain.setThirdPhase(0);

        when(stockMapper.selectVoList(any(Wrapper.class))).thenReturn(List.of(third, plain));
        when(locationInfoMapper.selectList(any())).thenReturn(List.of());

        List<LocationStockVo> rows = service.queryList(new LocationStockQuery());

        // 按 id 取，不依赖返回顺序（row223 起合并后有自己的排序口径）
        assertThat(rows).extracting(LocationStockVo::getId, LocationStockVo::getPlotLabel)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(90001L, "三期"),
                org.assertj.core.groups.Tuple.tuple(90002L, "-"));
        verify(plotInfoMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("insertByBo: happy → operatorId 走 LoginHelper.getUserId() 注入（ADR-0007）+ isEnd 默认 0")
    void testInsertByBo_OperatorIdInjected() {
        LocationStockBo bo = sampleBo();
        when(stockMapper.insert(any(LocationStock.class))).thenAnswer(inv -> {
            LocationStock e = inv.getArgument(0);
            e.setId(80001L);
            return 1;
        });

        int rows = service.insertByBo(bo);

        assertThat(rows).isEqualTo(1);
        ArgumentCaptor<LocationStock> captor = ArgumentCaptor.forClass(LocationStock.class);
        verify(stockMapper, times(1)).insert(captor.capture());
        LocationStock saved = captor.getValue();
        assertThat(saved.getOperatorId()).as("ADR-0007 强制 operatorId").isEqualTo(10086L);
        assertThat(saved.getIsEnd()).as("isEnd 默认 0").isEqualTo(0);
        assertThat(saved.getProductId()).isEqualTo(50001L);
    }

    @Test
    @DisplayName("insertByBo: error → productId + earNo 同时填，抛 stock.dimension.exclusive")
    void testInsertByBo_Dimension_BothProductIdAndEarNo() {
        LocationStockBo bo = sampleBo();
        bo.setEarNo("01A12605001");  // productId 已有 → 同时填 → 违规

        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("stock.dimension.exclusive");

        verify(stockMapper, times(0)).insert(any(LocationStock.class));
    }

    @Test
    @DisplayName("insertByBo: error → productId / earNo / plotId / medicineId 全空，抛 stock.dimension.exclusive")
    void testInsertByBo_Dimension_AllNull() {
        LocationStockBo bo = sampleBo();
        bo.setProductId(null);
        // earNo / plotId / medicineId 默认就是 null

        assertThatThrownBy(() -> service.insertByBo(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("stock.dimension.exclusive");
    }

    // -------- productOut（F0-8 验收门 · F0-1：出库按所选行 id 扣，组内其他篮子行不动） --------

    private LocationStock stockRowA() {
        LocationStock row = new LocationStock();
        row.setId(111L);
        row.setLocationId(90001L);
        row.setProductId(50001L);
        row.setProductName("猪后腿肉");
        row.setProductStock(new BigDecimal("10"));
        return row;
    }

    private StockOutBo stockOutBo(BigDecimal qty) {
        StockOutBo bo = new StockOutBo();
        bo.setStockIds(java.util.List.of(111L));
        bo.setQuantity(qty);
        bo.setStockOutDest("dept_pick");
        bo.setRemark("ut-productout");
        return bo;
    }

    private void stubProductOutCommon() {
        when(stockMapper.selectById(111L)).thenReturn(stockRowA());
        org.dromara.djs.warehouse.product.domain.ProductInfo product =
            new org.dromara.djs.warehouse.product.domain.ProductInfo();
        product.setId(50001L);
        product.setProductName("猪后腿肉");
        product.setProductUnit("kg");
        when(productInfoMapper.selectById(50001L)).thenReturn(product);
        when(bizCodeGenerator.generate(any(), any())).thenReturn("FAKE_FLOW_NO");
        when(stockFlowMapper.insert(any(StockFlow.class))).thenAnswer(inv -> {
            StockFlow f = inv.getArgument(0);
            f.setId(50003L);
            return 1;
        });
    }

    @Test
    @DisplayName("productOut happy（F0-1）：按所选行 id=111 原子扣 5（组维度 deductByProductLocation 绝不发生 → 同组行 B 余量不变）+ 流水 backstage_out/OT/-5")
    void testProductOut_DeductsSelectedRowOnly() {
        stubProductOutCommon();
        when(stockMapper.deductStockById(eq(111L), any(BigDecimal.class), eq(10086L))).thenReturn(1);

        java.util.List<Long> flowIds = service.productOut(stockOutBo(new BigDecimal("5")));
        assertThat(flowIds).containsExactly(50003L);

        // F0-1 核心：只扣所选行（id=111，且仅 1 次）；组维度扣减 API 不被调用 → 同 (库位,产品) 其他耳号/地块/白条篮不串扣
        verify(stockMapper, times(1)).deductStockById(eq(111L), eq(new BigDecimal("5")), eq(10086L));
        verify(stockMapper, times(1)).deductStockById(anyLong(), any(BigDecimal.class), anyLong());
        verify(stockMapper, never()).deductByProductLocation(any(), any(), any(), any());

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getFlowType()).isEqualTo("backstage_out");
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-5");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("5");
        assertThat(f.getWarehouseId()).isEqualTo(90001L);
        assertThat(f.getProductId()).isEqualTo(50001L);
        // 出库操作人 + 用户备注：这两列没人断言过，曾被整行删掉且编译/单测全绿 ——
        // 结果「毛菜间出库管理」「出库记录」的出库操作人列全空、出库弹框填的备注静默丢失。
        assertThat(f.getOperatorId()).isEqualTo(10086L);
        assertThat(f.getRemark()).isEqualTo("ut-productout");
    }

    @Test
    @DisplayName("productOut：行级扣减 affected=0（并发占用）→ 抛'库存不足或已被并发占用'（@Transactional 连流水回滚）")
    void testProductOut_ConcurrentLost_Throws() {
        stubProductOutCommon();
        when(stockMapper.deductStockById(anyLong(), any(BigDecimal.class), anyLong())).thenReturn(0);

        // 断言「整单撤销 + 让工人刷新重试」这两个语义，不钉死整句文案。
        // 报错里给的余量必须标明是「本单开始时」的快照 —— 按组出库时分配方案在事务开始时算好，
        // 扣到某一篮时它可能已被别人领走，把快照值直接叫「当前库存」会给出一个与页面、
        // 与库里都对不上的数字，工人只能反复重试。
        assertThatThrownBy(() -> service.productOut(stockOutBo(new BigDecimal("5"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("已被其他人领走")
            .hasMessageContaining("本单已全部撤销")
            .hasMessageContaining("本单开始时该篮余量");
    }

    // -------- 【三期】标识继承（V6 row92）：出的是哪一篮，流水就带哪个标识 --------

    /**
     * 出库流水的 {@code third_phase} 必须<b>由本方法从被扣的库存行读出来</b>，
     * 而不是靠调用方事后 patch —— 靠调用方补已经漏过一次：毛菜间出库补了，
     * 库存查询页每行的「产品出库」没补，「三期总出库」直接少算这一整条入口的量。
     */
    @Test
    @DisplayName("productOut：被扣行 third_phase=1 → 出库流水继承 1（三期总出库不漏计、出库记录地块列渲染「三期」）")
    void testProductOut_InheritsThirdPhaseFromDeductedRow() {
        LocationStock thirdPhaseRow = stockRowA();
        thirdPhaseRow.setThirdPhase(1);
        when(stockMapper.selectById(111L)).thenReturn(thirdPhaseRow);
        stubProductOutCommonExceptStock();
        when(stockMapper.deductStockById(eq(111L), any(BigDecimal.class), eq(10086L))).thenReturn(1);

        service.productOut(stockOutBo(new BigDecimal("5")));

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getThirdPhase()).isEqualTo(1);
    }

    @Test
    @DisplayName("productOut：普通行 third_phase=0 → 流水 0（不误标，三期统计不虚增）")
    void testProductOut_NormalRowKeepsZero() {
        LocationStock normalRow = stockRowA();
        normalRow.setThirdPhase(0);
        when(stockMapper.selectById(111L)).thenReturn(normalRow);
        stubProductOutCommonExceptStock();
        when(stockMapper.deductStockById(eq(111L), any(BigDecimal.class), eq(10086L))).thenReturn(1);

        service.productOut(stockOutBo(new BigDecimal("5")));

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getThirdPhase()).isEqualTo(0);
    }

    /**
     * 迁移前建的存量库存行该列读出来是 {@code null}，而 {@code t_warehouse_stock_flow.third_phase}
     * 是 {@code NOT NULL} —— 必须在这里归一成 0，否则这些行一出库就写库失败。
     */
    @Test
    @DisplayName("productOut：存量行 third_phase=null → 归一成 0 写流水（NOT NULL 列不能收 null）")
    void testProductOut_NullThirdPhaseNormalizedToZero() {
        LocationStock legacyRow = stockRowA();
        legacyRow.setThirdPhase(null);
        when(stockMapper.selectById(111L)).thenReturn(legacyRow);
        stubProductOutCommonExceptStock();
        when(stockMapper.deductStockById(eq(111L), any(BigDecimal.class), eq(10086L))).thenReturn(1);

        service.productOut(stockOutBo(new BigDecimal("5")));

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getThirdPhase()).isEqualTo(0);
    }

    /** {@link #stubProductOutCommon()} 去掉 stock 行 stub 的版本（由调用方自己塞不同 third_phase 的行）。 */
    private void stubProductOutCommonExceptStock() {
        org.dromara.djs.warehouse.product.domain.ProductInfo product =
            new org.dromara.djs.warehouse.product.domain.ProductInfo();
        product.setId(50001L);
        product.setProductName("猪后腿肉");
        product.setProductUnit("kg");
        when(productInfoMapper.selectById(50001L)).thenReturn(product);
        when(bizCodeGenerator.generate(any(), any())).thenReturn("FAKE_FLOW_NO");
        when(stockFlowMapper.insert(any(StockFlow.class))).thenAnswer(inv -> {
            StockFlow f = inv.getArgument(0);
            f.setId(50003L);
            return 1;
        });
    }

    /**
     * 转移 = 同一批货换个库位：两侧流水都要带标识，<b>目标篮也必须落在同一侧</b>。
     * 目标侧若走普通 UPSERT，转来的三期货会被并进冻品库的普通篮，从此两本账再分不开。
     */
    @Test
    @DisplayName("pigTransfer：源行 third_phase=1 → 出/入两条流水都带 1，且目标侧走三期专属 UPSERT")
    void testPigTransfer_InheritsThirdPhaseOnBothSidesAndRoutesUpsert() {
        LocationStock src = stockRowA();
        src.setThirdPhase(1);
        when(stockMapper.selectById(111L)).thenReturn(src);

        LocationInfo srcLoc = new LocationInfo();
        srcLoc.setId(90001L);
        srcLoc.setLocationName("猪肉鲜品库");
        when(locationInfoMapper.selectById(90001L)).thenReturn(srcLoc);
        LocationInfo frozen = new LocationInfo();
        frozen.setId(90002L);
        frozen.setLocationName("冻品库");
        when(locationInfoMapper.selectOne(any(Wrapper.class))).thenReturn(frozen);

        org.dromara.djs.warehouse.product.domain.ProductInfo product =
            new org.dromara.djs.warehouse.product.domain.ProductInfo();
        product.setId(50001L);
        product.setProductName("猪后腿肉");
        product.setProductUnit("kg");
        product.setBelongType("pork");
        when(productInfoMapper.selectById(50001L)).thenReturn(product);
        when(bizCodeGenerator.generate(any(), any())).thenReturn("FAKE_FLOW_NO");
        when(stockFlowMapper.insert(any(StockFlow.class))).thenAnswer(inv -> {
            StockFlow f = inv.getArgument(0);
            f.setId(50004L);
            return 1;
        });
        when(stockMapper.deductStockById(eq(111L), any(BigDecimal.class), eq(10086L))).thenReturn(1);
        when(stockMapper.addByProductLocationThirdPhase(eq(90002L), eq(50001L), any(BigDecimal.class), eq(10086L)))
            .thenReturn(1);

        org.dromara.djs.warehouse.stock.domain.bo.StockTransferBo bo =
            new org.dromara.djs.warehouse.stock.domain.bo.StockTransferBo();
        bo.setStockIds(java.util.List.of(111L));
        bo.setQuantity(new BigDecimal("5"));
        service.pigTransfer(bo);

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(2)).insert(cap.capture());
        assertThat(cap.getAllValues()).extracting(StockFlow::getThirdPhase).containsExactly(1, 1);
        // 目标侧必须走三期专属 UPSERT，绝不能并进冻品库的普通篮
        verify(stockMapper, times(1))
            .addByProductLocationThirdPhase(eq(90002L), eq(50001L), eq(new BigDecimal("5")), eq(10086L));
        verify(stockMapper, never()).addByProductLocation(any(), any(), any(), any());
    }

    // ── V6 row223 / D-0068：列表合并 + 内存分页 ────────────────────────────

    private LocationStockVo vo(Long id, Long productId, Long locationId, Long plotId,
                              String earNo, String whiteBarNo, String stock, java.util.Date createTime) {
        LocationStockVo v = new LocationStockVo();
        v.setId(id);
        v.setProductId(productId);
        v.setLocationId(locationId);
        v.setPlotId(plotId);
        v.setEarNo(earNo);
        v.setWhiteBarNo(whiteBarNo);
        v.setThirdPhase(0);
        v.setProductStock(new java.math.BigDecimal(stock));
        v.setCreateTime(createTime);
        return v;
    }

    private static java.util.Date at(String iso) {
        return java.util.Date.from(java.time.LocalDateTime.parse(iso)
            .atZone(java.time.ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("row223：同产品同库位同地块的多篮合成一行、库存取和，stockIds 按建篮时间升序（= 出库先扣的顺序）")
    void testMergedList_sumsAndKeepsFifoOrder() {
        // 故意把晚建的篮排在前面返回，验证服务端自己会重排成先进先出序
        when(stockMapper.selectVoList(any(Wrapper.class))).thenReturn(List.of(
            vo(2L, 10L, 900L, 20L, null, null, "32.000", at("2026-08-06T09:38:00")),
            vo(1L, 10L, 900L, 20L, null, null, "42.000", at("2026-08-06T09:37:37"))));
        when(locationInfoMapper.selectList(any())).thenReturn(List.of());

        List<LocationStockVo> rows = service.queryList(new LocationStockQuery());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getProductStock()).isEqualByComparingTo("74.000");
        assertThat(rows.get(0).getBasketCount()).isEqualTo(2);
        assertThat(rows.get(0).getStockIds()).containsExactly(1L, 2L);
        // 备注是单篮字段，合并行上必须清掉（否则整行看着像那一篮的来历）
        assertThat(rows.get(0).getRemark()).isNull();
    }

    @Test
    @DisplayName("row223：耳号 / 地块 / 白条流水号任一不同都各自成行（甲方：耳号和地块不同时需要分开显示）")
    void testMergedList_splitsOnVisibleDimensions() {
        when(stockMapper.selectVoList(any(Wrapper.class))).thenReturn(List.of(
            vo(1L, 10L, 900L, 20L, null, null, "42.000", at("2026-08-06T09:00:00")),
            vo(2L, 10L, 900L, 21L, null, null, "20.000", at("2026-08-06T09:01:00")),
            vo(3L, 11L, 901L, null, "01-01-1-251109-001", "BAR2609070002", "65.000", at("2026-09-07T10:40:21")),
            vo(4L, 11L, 901L, null, "01-01-1-251109-001", "BAR2609070003", "45.000", at("2026-09-07T10:40:28"))));
        when(locationInfoMapper.selectList(any())).thenReturn(List.of());

        List<LocationStockVo> rows = service.queryList(new LocationStockQuery());

        assertThat(rows).hasSize(4);
        assertThat(rows).allSatisfy(r -> assertThat(r.getBasketCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("row223：内存分页不能被畸形参数打崩（负 pageSize / 超大 pageNum 曾溢出成负下标 → 500）")
    void testPaging_clampsHostileParams() {
        when(stockMapper.selectVoList(any(Wrapper.class))).thenReturn(List.of(
            vo(1L, 10L, 900L, 20L, null, null, "42.000", at("2026-08-06T09:00:00")),
            vo(2L, 11L, 900L, 21L, null, null, "20.000", at("2026-08-06T09:01:00"))));
        when(locationInfoMapper.selectList(any())).thenReturn(List.of());

        // PageQuery(pageSize, pageNum)
        assertThat(service.queryPageList(new LocationStockQuery(), new PageQuery(-5, 1)).getRows())
            .as("负 pageSize 回落成查全部，不是崩").hasSize(2);
        assertThat(service.queryPageList(new LocationStockQuery(), new PageQuery(10, 300000000)).getRows())
            .as("超大页码只是空页，不是 500").isEmpty();
        assertThat(service.queryPageList(new LocationStockQuery(), new PageQuery(null, null)).getRows())
            .as("不传分页参数沿用旧语义：查全部").hasSize(2);
    }
}
