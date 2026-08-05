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
import org.dromara.djs.warehouse.veg.domain.VegetableHandle;
import org.dromara.djs.warehouse.veg.mapper.FeedLogMapper;
import org.dromara.djs.warehouse.veg.mapper.VegetableHandleMapper;
import org.dromara.djs.warehouse.vegout.domain.bo.VegOutItemBo;
import org.dromara.djs.warehouse.vegout.domain.bo.VegOutSubmitBo;
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
 * ② 饲料饲喂去向缺行时降级放行但有机饲喂记录照写；③ 库位/业态前置校验真的拦得住。</p>
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
    @DisplayName("果蔬月台：累加 send_platform_weight，并回写流水的 batch_no / plot_id")
    void vegDock_accumulatesPlatformWeight() {
        when(locationStockMapper.selectById(1L)).thenReturn(mkStock(1L, 10L, 20L));
        when(productInfoMapper.selectById(10L)).thenReturn(mkVegProduct(10L));
        stubHandleFound(30L, 20L);

        String batchNo = service.submit(mkBo("veg_dock", 1L, "12.000"), true);

        // row192：单号改走统一编码生成器，7 位纯数字（旧格式 VO+时间戳+4位随机 已废弃）
        assertThat(batchNo).isEqualTo("0000001");
        ArgumentCaptor<VegetableHandle> hc = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(vegetableHandleMapper).updateById(hc.capture());
        assertThat(hc.getValue().getSendPlatformWeight()).isEqualByComparingTo("22.000");
        assertThat(hc.getValue().getFeedWeight()).as("月台去向不应动饲料量").isNull();
        // 不写有机饲喂记录
        verify(feedLogMapper, never()).insert(any(FeedLog.class));

        ArgumentCaptor<StockFlow> fc = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper).updateById(fc.capture());
        assertThat(fc.getValue().getBatchNo()).isEqualTo(batchNo);
        assertThat(fc.getValue().getPlotId()).isEqualTo(20L);

        // 日统计「发往月台果蔬总重」读的是 handle_record 而非 vegetable_handle，必须同步写一条
        ArgumentCaptor<org.dromara.djs.warehouse.veg.domain.HandleRecord> rc =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.veg.domain.HandleRecord.class);
        verify(handleRecordMapper).insert(rc.capture());
        assertThat(rc.getValue().getHandleTarget()).as("handle_target=2 月台").isEqualTo(2);
        assertThat(rc.getValue().getRecordWeight()).isEqualByComparingTo("12.000");
        assertThat(rc.getValue().getHandleId()).isEqualTo(77L);
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
        // 补建行各重量记 0（这批货没走过毛菜处理流程）
        assertThat(created.getValue().getPickedWeight()).isEqualByComparingTo("0");
        assertThat(created.getValue().getStockInWeight()).isEqualByComparingTo("0");

        ArgumentCaptor<VegetableHandle> upd = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(vegetableHandleMapper).updateById(upd.capture());
        assertThat(upd.getValue().getSendPlatformWeight()).as("0 + 12 = 12").isEqualByComparingTo("12.000");
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
    @DisplayName("饲料饲喂：写有机饲喂记录 feed_type=veg_handle（位置=毛菜间）+ 累加 feed_weight")
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

        ArgumentCaptor<VegetableHandle> hc = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(vegetableHandleMapper).updateById(hc.capture());
        assertThat(hc.getValue().getFeedWeight()).isEqualByComparingTo("13.000");
        // 饲料去向不进「发往月台」日统计
        verify(handleRecordMapper, never()).insert(any(org.dromara.djs.warehouse.veg.domain.HandleRecord.class));
    }

    @Test
    @DisplayName("饲料饲喂：毛菜处理行缺失时降级放行，有机饲喂记录照写（与月台去向区别对待）")
    void feed_missingHandleRow_degradesButStillLogs() {
        when(locationStockMapper.selectById(1L)).thenReturn(mkStock(1L, 10L, null));
        when(productInfoMapper.selectById(10L)).thenReturn(mkVegProduct(10L));
        when(cropInfoMapper.selectOne(any())).thenReturn(null);
        when(vegetableHandleMapper.selectOne(any())).thenReturn(null);

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
