package org.dromara.djs.warehouse.veg.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.plant.crop.domain.CropInfo;
import org.dromara.djs.plant.crop.mapper.CropInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.veg.domain.HandleRecord;
import org.dromara.djs.warehouse.veg.domain.PlantingRecord;
import org.dromara.djs.warehouse.veg.domain.VegetableHandle;
import org.dromara.djs.warehouse.veg.domain.bo.HandleRecordSubmitBo;
import org.dromara.djs.warehouse.veg.domain.query.PickDetailQuery;
import org.dromara.djs.warehouse.veg.domain.vo.PickDetailVo;
import org.dromara.djs.warehouse.veg.domain.vo.VegPlotDetailVo;
import org.dromara.djs.warehouse.veg.mapper.HandleRecordMapper;
import org.dromara.djs.warehouse.veg.mapper.PlantingRecordMapper;
import org.dromara.djs.warehouse.veg.mapper.VegetableHandleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VegetableHandleServiceImpl} 单测（WMS-VEG-001）。
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>采收 happy：首次 record_type=1 → 创建 vegetable_handle + INSERT record + picked 累加</li>
 *   <li>处理-入库 happy：record_type=2 + handle_target=1 → stock_in 累加 + stock_flow IN</li>
 *   <li>处理-饲料 happy：record_type=2 + handle_target=3 → feed 累加 + 不写 stock_flow</li>
 *   <li>handle_target=1 但缺 location → 抛 + 任何 INSERT 不调用</li>
 *   <li>plantingRecord done → 抛</li>
 * </ol>
 *
 * @author djs
 * @since WMS-VEG-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VegetableHandleServiceImpl 单元测试")
class VegetableHandleServiceImplTest {

    @Mock
    private VegetableHandleMapper handleMapper;
    @Mock
    private HandleRecordMapper recordMapper;
    @Mock
    private org.dromara.djs.warehouse.veg.mapper.HandleRecordTeamMapper recordTeamMapper;
    @Mock
    private PlantingRecordMapper plantingRecordMapper;
    @Mock
    private StockFlowMapper stockFlowMapper;
    @Mock
    private LocationInfoMapper locationInfoMapper;
    @Mock
    private IBizCodeGenerator bizCodeGenerator;
    @Mock
    private org.dromara.djs.common.image.service.ImageUrlResolver imageUrlResolver;
    @Mock
    private CropInfoMapper cropInfoMapper;
    @Mock
    private org.dromara.djs.warehouse.veg.mapper.FeedLogMapper feedLogMapper;
    @Mock
    private org.dromara.djs.plant.plan.mapper.PlantDetailsMapper plantDetailsMapper;
    @Mock
    private org.dromara.djs.warehouse.stock.mapper.LocationStockMapper locationStockMapper;
    @Mock
    private org.dromara.djs.warehouse.product.mapper.ProductInfoMapper productInfoMapper;
    @Mock
    private org.dromara.djs.warehouse.loss.service.ILossFlowService lossFlowService;
    @Mock
    private org.dromara.djs.plant.activity.service.IPlantActivityService plantActivityService;
    @Mock
    private org.dromara.djs.plant.crop.service.ICropProductService cropProductService;

    private VegetableHandleServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeEach
    void setup() {
        service = new VegetableHandleServiceImpl(
            handleMapper, recordMapper, recordTeamMapper, plantingRecordMapper, stockFlowMapper,
            locationInfoMapper, bizCodeGenerator, imageUrlResolver, cropInfoMapper, feedLogMapper,
            plantDetailsMapper, locationStockMapper, productInfoMapper, lossFlowService, plantActivityService,
            cropProductService);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(9001L);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    // -------- V6 row102 公共夹具（毛菜保鲜库 L0006 + 地块篮） --------

    /** 毛菜鲜品库库位 id（按 location_code='L0006' 解析出来的那个）。 */
    private static final Long FRESH_LOC_ID = 96001L;

    /** 地块 11001 上作物 12001 的产出产品。 */
    private static final Long PRODUCT_ID = 88001L;

    /** L0006 已在库位主数据里维护（改造后采摘入库 / 处理出库都要用它，缺了直接 fail-fast）。 */
    private void mockFreshLocation() {
        LocationInfo loc = new LocationInfo();
        loc.setId(FRESH_LOC_ID);
        loc.setLocationCode("L0006");
        loc.setLocationName("毛菜鲜品库");
        when(locationInfoMapper.selectOne(any())).thenReturn(loc);
    }

    /** 作物 12001 配了产出产品（{@code related_product}），且产品主数据存在。 */
    private void mockCropProduct() {
        CropInfo crop = new CropInfo();
        crop.setId(12001L);
        crop.setRelatedProduct(PRODUCT_ID);
        when(cropInfoMapper.selectById(12001L)).thenReturn(crop);
        org.dromara.djs.warehouse.product.domain.ProductInfo p =
            new org.dromara.djs.warehouse.product.domain.ProductInfo();
        p.setId(PRODUCT_ID);
        p.setProductName("小白菜");
        p.setProductUnit("kg");
        when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(p);
    }

    /** 本条种植记录（60001）的 id —— 篮子的来源标识 {@code source_biz_id}。 */
    private static final Long SOURCE_BIZ_ID = 60001L;

    /** 造一个地块篮（id 从 50001 起，便于断言 FIFO 顺序），来源 = 种植记录 60001。 */
    private org.dromara.djs.warehouse.stock.domain.LocationStock basket(Long id, String stock) {
        org.dromara.djs.warehouse.stock.domain.LocationStock b =
            new org.dromara.djs.warehouse.stock.domain.LocationStock();
        b.setId(id);
        b.setLocationId(FRESH_LOC_ID);
        b.setProductId(PRODUCT_ID);
        b.setPlotId(11001L);
        b.setSourceBizId(SOURCE_BIZ_ID);
        b.setProductStock(new BigDecimal(stock));
        return b;
    }

    /**
     * 毛菜保鲜库里<b>本条种植记录</b>该产品现有这几篮（按 FIFO 顺序），合计自动喂给上限校验。
     *
     * <p>stub 键带 {@code SOURCE_BIZ_ID}：service 少传这个参数就 stub 不中、拿到空篮子列表，
     * 本身就是一道回归防线。</p>
     */
    private void mockFreshBaskets(String... stocks) {
        List<org.dromara.djs.warehouse.stock.domain.LocationStock> baskets = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        long id = 50001L;
        for (String s : stocks) {
            baskets.add(basket(id++, s));
            total = total.add(new BigDecimal(s));
        }
        when(locationStockMapper.sumPlotProductStock(FRESH_LOC_ID, PRODUCT_ID, 11001L, SOURCE_BIZ_ID))
            .thenReturn(total);
        when(locationStockMapper.selectPlotProductBaskets(FRESH_LOC_ID, PRODUCT_ID, 11001L, SOURCE_BIZ_ID))
            .thenReturn(baskets);
        // 收口结转走的是「按来源取全部篮」（不限产品）—— 与上面那条 stub 同一批篮子，
        // 因为本夹具只造了一个产品。多产品场景见 mockFreshBasketsBySource。
        when(locationStockMapper.selectBasketsBySource(FRESH_LOC_ID, SOURCE_BIZ_ID)).thenReturn(baskets);
        when(locationStockMapper.deductStockById(anyLong(), any(BigDecimal.class), anyLong())).thenReturn(1);
    }

    private PlantingRecord samplePlanting(String status) {
        PlantingRecord p = new PlantingRecord();
        p.setId(60001L);
        p.setPlotId(11001L);
        p.setCropId(12001L);
        p.setPlotName("地块A");
        p.setCropName("卷心菜");
        p.setHarvestWeight(new BigDecimal("50.000"));
        p.setHandleStatus(status);
        return p;
    }

    // -------- F-4：遗留通用录入入口 /submit 全面停用 --------

    /** 遗留入口 BO（三个分支共用）。 */
    private HandleRecordSubmitBo legacyBo(Integer recordType, Integer handleTarget, String weight) {
        HandleRecordSubmitBo bo = new HandleRecordSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setRecordType(recordType);
        bo.setHandleTarget(handleTarget);
        bo.setRecordWeight(new BigDecimal(weight));
        return bo;
    }

    @Test
    @DisplayName("F-4①：遗留 /submit 采收分支（recordType=1）→ 拒。它只加 picked、不建库存篮，货对新链路隐形")
    void testLegacySubmit_Pick_Rejected() {
        assertThatThrownBy(() -> service.submitHandleRecord(legacyBo(1, null, "50.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("该录入入口已停用")
            .hasMessageContaining("/harvest");
    }

    @Test
    @DisplayName("F-4②：遗留 /submit 入库分支（recordType=2 + target=1）→ 拒。它能建篮，而 /process 对同样的值是硬拒的")
    void testLegacySubmit_StockInTarget_Rejected() {
        assertThatThrownBy(() -> service.submitHandleRecord(legacyBo(2, 1, "10.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("该录入入口已停用")
            .hasMessageContaining("/process");
    }

    @Test
    @DisplayName("F-4③：遗留 /submit 月台 / 饲料分支（target=2/3）→ 拒。它加 handled 却不扣库存，收口时同一批 kg 再算一次损耗")
    void testLegacySubmit_PlatformAndFeed_Rejected() {
        assertThatThrownBy(() -> service.submitHandleRecord(legacyBo(2, 2, "10.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("该录入入口已停用");
        assertThatThrownBy(() -> service.submitHandleRecord(legacyBo(2, 3, "10.000")))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("该录入入口已停用");
    }

    @Test
    @DisplayName("F-4④：遗留 /submit 一行都不落 —— 连 planting_record 都不查，任何 mapper 零交互")
    void testLegacySubmit_TouchesNothing() {
        // 参数再怎么合法也进不去（连"记录不存在"这类前置校验都到不了）
        assertThatThrownBy(() -> service.submitHandleRecord(legacyBo(1, null, "50.000")))
            .isInstanceOf(ServiceException.class);

        Mockito.verifyNoInteractions(plantingRecordMapper);
        Mockito.verifyNoInteractions(handleMapper);
        Mockito.verifyNoInteractions(recordMapper);
        Mockito.verifyNoInteractions(stockFlowMapper);
        Mockito.verifyNoInteractions(locationStockMapper);
        Mockito.verifyNoInteractions(lossFlowService);
    }

    // -------- F-1：结转损耗只结自己那条种植记录名下的货 --------

    /**
     * 另一条种植记录（同地块同作物）名下的篮子 —— 它<b>永远不该</b>被 60001 这条记录碰到。
     * id 用 59xxx 段，与本记录的 50xxx 段区分，断言里一眼看得出串没串。
     */
    private org.dromara.djs.warehouse.stock.domain.LocationStock foreignBasket(String stock) {
        org.dromara.djs.warehouse.stock.domain.LocationStock b =
            new org.dromara.djs.warehouse.stock.domain.LocationStock();
        b.setId(59001L);
        b.setLocationId(FRESH_LOC_ID);
        b.setProductId(PRODUCT_ID);
        b.setPlotId(11001L);          // 同一块地
        b.setSourceBizId(60002L);     // 另一条种植记录
        b.setProductStock(new BigDecimal(stock));
        return b;
    }

    @Test
    @DisplayName("F-1①：收口按 source_biz_id = 本条种植记录 id 取篮（不带就会扣同地块别人的货）")
    void testFinish_SettleQueriesOwnSourceOnly() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("40.000", "0", 1));
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(1);
        mockCropProduct();
        mockFreshLocation();
        mockFreshBaskets("15.000");

        service.submitProcess(processBo(null, "0", 1));

        // 取篮的第二个参数必须是本条种植记录 id
        verify(locationStockMapper).selectBasketsBySource(FRESH_LOC_ID, 60001L);
        // 只结自己的 15kg
        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        assertThat(updCap.getValue().getLossWeight()).isEqualByComparingTo("15.000");
    }

    @Test
    @DisplayName("F-1④：收口范围与读侧同源 —— 产品被移出作物配置后，它的篮子照样结转（picked = handled + loss）")
    void testFinish_SettlesProductRemovedFromCropConfig() {
        // QA 活体复现：作物配 P1(88001)/P2(77001) 各采 30/20kg，删掉 P2 的作物产品配置后收口。
        // 第二轮按「作物产品配置 ∪ crop.related_product ∪ handle.product_id」枚举产品，P2 已不在任何一处
        //（handle.product_id 是建汇总行时写死的单值 = crop.related_product，不是采收过的产品集合）
        // → loss 只有 P1 的 30，P2 的 20kg 一分没结没扣：篮子成僵尸、picked(50) ≠ handled(0) + loss(30)。
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "0", 1));
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(1);
        mockFreshLocation();
        // 作物配置里现在只剩 P1（P2 已被删）
        CropInfo crop = new CropInfo();
        crop.setId(12001L);
        crop.setRelatedProduct(PRODUCT_ID);
        when(cropInfoMapper.selectById(12001L)).thenReturn(crop);
        org.dromara.djs.plant.crop.domain.vo.CropProductVo onlyP1 =
            new org.dromara.djs.plant.crop.domain.vo.CropProductVo();
        onlyP1.setProductId(PRODUCT_ID);
        when(cropProductService.listByCrop(12001L)).thenReturn(List.of(onlyP1));
        // 但库里两个产品的篮都还在（读侧只按 source_biz_id 查，两篮都看得见）
        org.dromara.djs.warehouse.stock.domain.LocationStock p1 = basket(50001L, "30.000");
        org.dromara.djs.warehouse.stock.domain.LocationStock p2 = basket(50002L, "20.000");
        p2.setProductId(77001L);
        when(locationStockMapper.selectBasketsBySource(FRESH_LOC_ID, 60001L)).thenReturn(List.of(p1, p2));
        when(locationStockMapper.deductStockById(anyLong(), any(BigDecimal.class), anyLong())).thenReturn(1);

        service.submitProcess(processBo(null, "0", 1));

        // 两篮都扣到 0
        ArgumentCaptor<Long> idCap = ArgumentCaptor.forClass(Long.class);
        verify(locationStockMapper, times(2)).deductStockById(idCap.capture(), any(BigDecimal.class), anyLong());
        assertThat(idCap.getAllValues()).containsExactly(50001L, 50002L);

        // 两条损耗流水，各记各的产品（篮子自带 product_id，不用反解）
        ArgumentCaptor<org.dromara.djs.warehouse.loss.domain.LossFlow> lossCap =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.loss.domain.LossFlow.class);
        verify(lossFlowService, times(2)).record(lossCap.capture());
        assertThat(lossCap.getAllValues()).extracting(
                org.dromara.djs.warehouse.loss.domain.LossFlow::getProductId)
            .containsExactly(PRODUCT_ID, 77001L);
        assertThat(lossCap.getAllValues().get(1).getLossWeight()).isEqualByComparingTo("20.000");

        // picked(50) = handled(0) + loss(50)，账自洽
        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        assertThat(updCap.getValue().getLossWeight()).isEqualByComparingTo("50.000");
    }

    @Test
    @DisplayName("F-1②：同地块另一条种植记录的篮子一分不动，损耗不会超过本记录的采摘量（loss ≤ picked）")
    void testFinish_ForeignRecordBasketsUntouched() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        // 本记录 picked=40、已处理 10 → 毛菜间还剩 30；同地块另一条记录还有 25kg 躺着
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("40.000", "10.000", 1));
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(1);
        mockCropProduct();
        mockFreshLocation();
        // 按来源分流：本记录看得到 30kg，另一条记录的 25kg 在另一个 sourceBizId 下
        when(locationStockMapper.selectBasketsBySource(FRESH_LOC_ID, 60001L))
            .thenReturn(List.of(basket(50001L, "30.000")));
        when(locationStockMapper.selectBasketsBySource(FRESH_LOC_ID, 60002L))
            .thenReturn(List.of(foreignBasket("25.000")));
        when(locationStockMapper.deductStockById(anyLong(), any(BigDecimal.class), anyLong())).thenReturn(1);

        service.submitProcess(processBo(null, "0", 1));

        // 只扣自己的篮
        verify(locationStockMapper, times(1)).deductStockById(eq(50001L), any(BigDecimal.class), anyLong());
        // 别人的篮一次都没碰（第一版实测：这里会扣 59001，把 B 的 25kg 结成 A 的损耗）
        verify(locationStockMapper, never()).deductStockById(eq(59001L), any(BigDecimal.class), anyLong());

        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        // 30 而不是 55；且 loss(30) + handled(10) = picked(40)，账自洽
        assertThat(updCap.getValue().getLossWeight()).isEqualByComparingTo("30.000");
        assertThat(updCap.getValue().getLossWeight())
            .as("结转损耗不可能超过本记录的采摘量")
            .isLessThanOrEqualTo(new BigDecimal("40.000"));

        ArgumentCaptor<org.dromara.djs.warehouse.loss.domain.LossFlow> lossCap =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.loss.domain.LossFlow.class);
        verify(lossFlowService, times(1)).record(lossCap.capture());
        assertThat(lossCap.getValue().getLossWeight()).isEqualByComparingTo("30.000");
    }

    @Test
    @DisplayName("F-1③：采摘活动直送毛菜保鲜室的篮（source_biz_id 为空）不参与任何一条种植记录的收口结转")
    void testFinish_PickActivityBasketsNotSettled() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("40.000", "40.000", 1));
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(1);
        mockCropProduct();
        mockFreshLocation();
        // 本记录的货已出干净；库里剩的全是采摘活动直送进来的（sourceBizId=null，本记录查不到）
        when(locationStockMapper.selectBasketsBySource(FRESH_LOC_ID, 60001L)).thenReturn(List.of());

        service.submitProcess(processBo(null, "0", 1));

        verify(locationStockMapper, never()).deductStockById(anyLong(), any(BigDecimal.class), anyLong());
        verify(lossFlowService, never()).record(any(org.dromara.djs.warehouse.loss.domain.LossFlow.class));
        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        assertThat(updCap.getValue().getLossWeight()).isEqualByComparingTo("0");
    }

    // -------- F-2：handled_weight 不可能超过本记录的 picked_weight --------

    @Test
    @DisplayName("F-2①：出库上限按本记录的库存算 —— 同地块别人还有货也抬不高上限（采摘 60 出 68 必须被拒）")
    void testProcess_CannotOutrunOwnPickedWeight() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        // 本记录采摘 60kg 全在库；同地块另一条记录另有 20kg（第一版会把它算进上限 → 68 放行）
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("60.000", "0", 1));
        mockCropProduct();
        mockFreshLocation();
        when(locationStockMapper.sumPlotProductStock(FRESH_LOC_ID, PRODUCT_ID, 11001L, 60001L))
            .thenReturn(new BigDecimal("60.000"));
        when(locationStockMapper.sumPlotProductStock(FRESH_LOC_ID, PRODUCT_ID, 11001L, 60002L))
            .thenReturn(new BigDecimal("20.000"));

        assertThatThrownBy(() -> service.submitProcess(processBo(2, "68.000", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("仅剩 60")
            .hasMessageContaining("无法出库 68");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
        verify(locationStockMapper, never()).deductStockById(anyLong(), any(BigDecimal.class), anyLong());
    }

    @Test
    @DisplayName("F-2②：上限校验与实际扣减用同一组键（含 source_biz_id），不会出现校验说够真扣时不够")
    void testProcess_CapAndDeductUseSameScope() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("60.000", "0", 1));
        mockCropProduct();
        mockFreshLocation();
        mockFreshBaskets("60.000");
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap())).thenReturn("F20260831OT0009");

        service.submitProcess(processBo(2, "60.000", 0));

        verify(locationStockMapper).sumPlotProductStock(FRESH_LOC_ID, PRODUCT_ID, 11001L, 60001L);
        verify(locationStockMapper).selectPlotProductBaskets(FRESH_LOC_ID, PRODUCT_ID, 11001L, 60001L);

        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        // handled 恰好等于 picked，一克不多
        assertThat(updCap.getValue().getHandledWeight()).isEqualByComparingTo("60.000");
    }

    // -------- F-3：剩余重量按种植记录隔离（同地块两条记录不共享一份库存）--------

    @Test
    @DisplayName("F-3：分产品剩余按 source_biz_id 归并 —— 同地块两条记录各拿各的，不再各显示同一份")
    void testListPlots_RemainKeyedByPlantingRecordNotPlot() {
        VegPlotDetailVo rowA = new VegPlotDetailVo();
        rowA.setPlantingRecordId(60001L);
        rowA.setPlotId(11001L);
        VegPlotDetailVo rowB = new VegPlotDetailVo();
        rowB.setPlantingRecordId(60002L);
        rowB.setPlotId(11001L);          // 同一块地上的另一条种植记录
        when(plantingRecordMapper.selectPlotDetailByCrop(12001L)).thenReturn(List.of(rowA, rowB));

        org.dromara.djs.plant.crop.domain.vo.CropProductVo cp =
            new org.dromara.djs.plant.crop.domain.vo.CropProductVo();
        cp.setProductId(PRODUCT_ID);
        cp.setProductName("小白菜");
        when(cropProductService.listByCrop(12001L)).thenReturn(List.of(cp));
        mockFreshLocation();

        org.dromara.djs.warehouse.stock.domain.vo.PlotProductStockRow sa =
            new org.dromara.djs.warehouse.stock.domain.vo.PlotProductStockRow();
        sa.setSourceBizId(60001L);
        sa.setProductId(PRODUCT_ID);
        sa.setStockWeight(new BigDecimal("40.000"));
        org.dromara.djs.warehouse.stock.domain.vo.PlotProductStockRow sb =
            new org.dromara.djs.warehouse.stock.domain.vo.PlotProductStockRow();
        sb.setSourceBizId(60002L);
        sb.setProductId(PRODUCT_ID);
        sb.setStockWeight(new BigDecimal("25.000"));
        when(locationStockMapper.selectPlotProductStocks(eq(FRESH_LOC_ID), any(java.util.Collection.class)))
            .thenReturn(List.of(sa, sb));

        List<VegPlotDetailVo> plots = service.listPlotsByCrop(12001L);

        // 第一版按 plot 归并：两行都会读到 65kg，mp 头卡 Σ remainWeight 显 130
        assertThat(plots.get(0).getProducts().get(0).getRemainWeight()).isEqualByComparingTo("40.000");
        assertThat(plots.get(1).getProducts().get(0).getRemainWeight()).isEqualByComparingTo("25.000");
        // 查库时下传的是种植记录 id 集合，不是地块 id 集合
        ArgumentCaptor<java.util.Collection<Long>> idsCap = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(locationStockMapper).selectPlotProductStocks(eq(FRESH_LOC_ID), idsCap.capture());
        assertThat(idsCap.getValue()).containsExactlyInAnyOrder(60001L, 60002L);
    }

    // -------- 来源标识写入：采摘录入写、采摘活动直送不写 --------

    @Test
    @DisplayName("采摘建篮必须带 source_biz_id = 本条种植记录 id（不带的话它对处理录入永远不可见）")
    void testHarvest_BasketCarriesSourceBizId() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("0"));
        mockCropProduct();
        mockFreshLocation();
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap())).thenReturn("F20260831IN0009");

        service.submitHarvest(harvestBo("30.000", 0, List.of(30001L)));

        ArgumentCaptor<org.dromara.djs.warehouse.stock.domain.LocationStock> basketCap =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.stock.domain.LocationStock.class);
        verify(locationStockMapper, times(1)).insert(basketCap.capture());
        assertThat(basketCap.getValue().getSourceBizId()).isEqualTo(60001L);
        assertThat(basketCap.getValue().getPlotId()).isEqualTo(11001L);
    }

    @Test
    @DisplayName("采摘活动直送毛菜保鲜室的篮 source_biz_id 留空 —— 它不属于任何一条种植记录")
    void testPickDestVegFresh_BasketHasNoSourceBizId() {
        mockCropProduct();
        mockFreshLocation();
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap())).thenReturn("F20260831IN0010");

        org.dromara.djs.warehouse.veg.domain.bo.PickDestSubmitBo bo =
            new org.dromara.djs.warehouse.veg.domain.bo.PickDestSubmitBo();
        bo.setCropId(12001L);
        bo.setCropName("卷心菜");
        bo.setPlotId(11001L);
        bo.setPickDest("veg_fresh");
        bo.setWeight(new BigDecimal("18.000"));
        bo.setRecorderId(9001L);
        service.recordPickDestination(bo);

        ArgumentCaptor<org.dromara.djs.warehouse.stock.domain.LocationStock> basketCap =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.stock.domain.LocationStock.class);
        verify(locationStockMapper, times(1)).insert(basketCap.capture());
        assertThat(basketCap.getValue().getSourceBizId())
            .as("活动直送的货被某条种植记录的收口结成损耗，正是第一版实测到的串货之一")
            .isNull();
        assertThat(basketCap.getValue().getPlotId()).isEqualTo(11001L);
    }

    // -------- submitProcess 新守门 + 损耗结算（客户 2026-06-20 序号6/9） --------

    private VegetableHandle sampleHandle(String picked, String handled, int isWeighed) {
        VegetableHandle h = new VegetableHandle();
        h.setId(70001L);
        h.setPlantingRecordId(60001L);
        h.setPlotId(11001L);
        h.setCropId(12001L);
        h.setPickedWeight(new BigDecimal(picked));
        h.setHandledWeight(new BigDecimal(handled));
        h.setFeedWeight(BigDecimal.ZERO);
        h.setSendPlatformWeight(BigDecimal.ZERO);
        // 行59 损耗口径 loss = picked − stockIn − sendPlatform − feed：
        // 保持台账自洽 handled = stockIn + sendPlatform（已处理量全记入库分量）
        h.setStockInWeight(new BigDecimal(handled));
        h.setLossWeight(BigDecimal.ZERO);
        h.setIsWeighed(isWeighed);
        h.setHandleStatus("processing");
        return h;
    }

    private org.dromara.djs.warehouse.veg.domain.bo.ProcessSubmitBo processBo(
        Integer target, String weight, int finish) {
        org.dromara.djs.warehouse.veg.domain.bo.ProcessSubmitBo bo =
            new org.dromara.djs.warehouse.veg.domain.bo.ProcessSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setHandleTarget(target);
        bo.setProcessWeight(new BigDecimal(weight));
        bo.setProcessFinish(finish);
        bo.setProcessUserId(9001L);
        return bo;
    }

    @Test
    @DisplayName("row102①：处理量超毛菜保鲜库该地块该产品的库存 → 抛「仅剩 X kg」，一行不落、一篮不扣")
    void testProcess_OverFreshStock_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "0", 1));
        mockCropProduct();
        mockFreshLocation();
        // 毛菜间只剩 5kg，本次要出 20kg
        mockFreshBaskets("5.000");

        assertThatThrownBy(() -> service.submitProcess(processBo(2, "20.000", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("仅剩 5")
            .hasMessageContaining("无法出库 20");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
        verify(locationStockMapper, never()).deductStockById(anyLong(), any(BigDecimal.class), anyLong());
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
    }

    @Test
    @DisplayName("序号9-Req2：未称重完成却勾处理完成 → 抛「请先完成地块称重」，不落库")
    void testProcess_FinishWithoutWeigh_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        // 库存够（不被上限拦），但 is_weighed=2 未称重完成 + processFinish=1 → 拦
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "10.000", 2));
        mockCropProduct();
        mockFreshLocation();
        mockFreshBaskets("40.000");

        assertThatThrownBy(() -> service.submitProcess(processBo(2, "10.000", 1)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("请先完成地块称重");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
        verify(locationStockMapper, never()).deductStockById(anyLong(), any(BigDecimal.class), anyLong());
    }

    @Test
    @DisplayName("row102②：去向=果蔬月台 → 跨篮 FIFO 扣毛菜间库存 + veg_stock_out(OT/veg_dock) 出库流水")
    void testProcess_ToPlatform_FifoDeductAndOutFlow() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "0", 1));
        mockCropProduct();
        mockFreshLocation();
        // 两篮：先进的 12kg + 后进的 20kg；本次出 18kg → 第一篮扣光 12，第二篮扣 6
        mockFreshBaskets("12.000", "20.000");
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap())).thenReturn("F20260831OT0001");

        service.submitProcess(processBo(2, "18.000", 0));

        // FIFO：按 selectPlotProductBaskets 返回顺序逐篮扣，先扣满再扣下一篮
        ArgumentCaptor<Long> idCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<BigDecimal> qtyCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(locationStockMapper, times(2)).deductStockById(idCap.capture(), qtyCap.capture(), eq(9001L));
        assertThat(idCap.getAllValues()).containsExactly(50001L, 50002L);
        assertThat(qtyCap.getAllValues().get(0)).isEqualByComparingTo("12.000");
        assertThat(qtyCap.getAllValues().get(1)).isEqualByComparingTo("6.000");

        // 一条出库流水
        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        StockFlow flow = flowCap.getValue();
        assertThat(flow.getFlowType()).isEqualTo("veg_stock_out");
        assertThat(flow.getInoutType()).isEqualTo("OT");
        assertThat(flow.getStockOutDest()).isEqualTo("veg_dock");
        assertThat(flow.getWarehouseId()).isEqualTo(FRESH_LOC_ID);
        assertThat(flow.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(flow.getPlotId()).isEqualTo(11001L);
        assertThat(flow.getChangeNum()).isEqualByComparingTo("18.000");

        // 汇总：月台 + 已处理 各 +18；stock_in 不动（它现在是采摘侧的账）
        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        VegetableHandle upd = updCap.getValue();
        assertThat(upd.getSendPlatformWeight()).isEqualByComparingTo("18.000");
        assertThat(upd.getHandledWeight()).isEqualByComparingTo("18.000");
        assertThat(upd.getFeedWeight()).isEqualByComparingTo("0");
        assertThat(upd.getStockInWeight()).isEqualByComparingTo("0");
        // 未收口 → 不结损耗
        assertThat(upd.getLossWeight()).isEqualByComparingTo("0");
        verify(lossFlowService, never()).record(any(org.dromara.djs.warehouse.loss.domain.LossFlow.class));
    }

    @Test
    @DisplayName("row102③：处理录入去向=有机饲喂 → 扣毛菜间库存(feed)，且【计入 handled】+ 写饲料台账")
    void testProcess_ToFeed_DeductsAndCountsIntoHandled() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "0", 1));
        mockCropProduct();
        mockFreshLocation();
        mockFreshBaskets("30.000");
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap())).thenReturn("F20260831OT0002");

        service.submitProcess(processBo(3, "10.000", 0));

        verify(locationStockMapper, times(1))
            .deductStockById(eq(50001L), any(BigDecimal.class), eq(9001L));

        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        assertThat(flowCap.getValue().getFlowType()).isEqualTo("veg_stock_out");
        assertThat(flowCap.getValue().getStockOutDest()).isEqualTo("feed");

        // 饲料台账照旧写
        verify(feedLogMapper, times(1)).insert(any(org.dromara.djs.warehouse.veg.domain.FeedLog.class));

        // 口径回归防线：处理录入的饲喂去向必须同时进 feed 和 handled。
        // ⚠️ 别把这条与「毛菜间出库管理」(VegOutServiceImpl) 混为一谈 —— 甲方 2026-08-19
        // loss = 地块入库量 − 果蔬月台 − 有机饲喂 − 出库，handled 只收本处（处理录入）的月台 + 饲喂，
        // 那个并列的「出库」项不进这一列。
        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        assertThat(updCap.getValue().getFeedWeight()).isEqualByComparingTo("10.000");
        assertThat(updCap.getValue().getHandledWeight()).isEqualByComparingTo("10.000");
        assertThat(updCap.getValue().getSendPlatformWeight()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("row102④：处理完成 → 毛菜间剩余库存全部转损耗（篮子扣到 0 + loss_flow），loss_weight = 结转量")
    void testProcess_Finish_SettlesRemainStockAsLoss() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "0", 1));
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(1);
        mockCropProduct();
        mockFreshLocation();
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap())).thenReturn("F20260831OT0003");
        // 毛菜间原有 30kg（一篮）；本次月台出 10kg（FIFO 按产品取篮）→ 出完剩 20kg，
        // 收口再按来源取全部篮（拿到的是扣后余量）把 20kg 结成损耗
        when(locationStockMapper.sumPlotProductStock(FRESH_LOC_ID, PRODUCT_ID, 11001L, SOURCE_BIZ_ID))
            .thenReturn(new BigDecimal("30.000"));
        when(locationStockMapper.selectPlotProductBaskets(FRESH_LOC_ID, PRODUCT_ID, 11001L, SOURCE_BIZ_ID))
            .thenReturn(List.of(basket(50001L, "30.000")));
        when(locationStockMapper.selectBasketsBySource(FRESH_LOC_ID, SOURCE_BIZ_ID))
            .thenReturn(List.of(basket(50001L, "20.000")));
        when(locationStockMapper.deductStockById(anyLong(), any(BigDecimal.class), anyLong())).thenReturn(1);

        service.submitProcess(processBo(2, "10.000", 1));

        // 两次扣减：本次出库 10 + 收口把剩下的 20 扣到 0（不允许只写损耗不扣库存）
        ArgumentCaptor<BigDecimal> qtyCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(locationStockMapper, times(2)).deductStockById(eq(50001L), qtyCap.capture(), eq(9001L));
        assertThat(qtyCap.getAllValues().get(0)).isEqualByComparingTo("10.000");
        assertThat(qtyCap.getAllValues().get(1)).isEqualByComparingTo("20.000");

        // 统一损耗台账：字段与既有毛菜处理损耗完全一致（每日损耗汇总据此自动统计）
        ArgumentCaptor<org.dromara.djs.warehouse.loss.domain.LossFlow> lossCap =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.loss.domain.LossFlow.class);
        verify(lossFlowService, times(1)).record(lossCap.capture());
        org.dromara.djs.warehouse.loss.domain.LossFlow loss = lossCap.getValue();
        assertThat(loss.getLossType()).isEqualTo("veg_handle_loss");
        assertThat(loss.getSourceBizType()).isEqualTo("veg_handle");
        assertThat(loss.getSourceBizId()).isEqualTo(70001L);
        assertThat(loss.getLossWeight()).isEqualByComparingTo("20.000");
        assertThat(loss.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(loss.getPlotId()).isEqualTo(11001L);
        assertThat(loss.getBelongType()).isEqualTo("vegetable");

        // loss_weight 列 = 本次结转量（不再用 picked − stockIn − sendPlatform − feed 减出来）
        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        VegetableHandle upd = updCap.getValue();
        assertThat(upd.getLossWeight()).isEqualByComparingTo("20.000");
        assertThat(upd.getHandledWeight()).isEqualByComparingTo("10.000");
        assertThat(upd.getIsFinish()).isEqualTo(1);
        assertThat(upd.getHandleStatus()).isEqualTo("done");
    }

    @Test
    @DisplayName("row102⑤：处理录入选【毛菜鲜品库】(target=1) → 直接拒（去向已取消），一行不落")
    void testProcess_StockInTarget_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));

        assertThatThrownBy(() -> service.submitProcess(processBo(1, "10.000", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("毛菜鲜品库去向已取消");
        // 0 kg 收口也一样拒（不给任何后门）
        assertThatThrownBy(() -> service.submitProcess(processBo(1, "0", 1)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("毛菜鲜品库去向已取消");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never()).insert(any(org.dromara.djs.warehouse.stock.domain.LocationStock.class));
    }

    @Test
    @DisplayName("row54：去向=饲料时 feed_log 落的是工人选的产品，不是作物默认产品")
    void testProcess_ToFeed_FeedLogUsesSelectedProduct() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "10.000", 1));
        // 作物默认产品是 88001（related_product），工人这次选的是 77001（如「红薯杆」）
        CropInfo crop = new CropInfo();
        crop.setId(12001L);
        crop.setRelatedProduct(88001L);
        when(cropInfoMapper.selectById(12001L)).thenReturn(crop);
        org.dromara.djs.plant.crop.domain.vo.CropProductVo a =
            new org.dromara.djs.plant.crop.domain.vo.CropProductVo();
        a.setProductId(88001L);
        org.dromara.djs.plant.crop.domain.vo.CropProductVo b =
            new org.dromara.djs.plant.crop.domain.vo.CropProductVo();
        b.setProductId(77001L);
        when(cropProductService.listByCrop(12001L)).thenReturn(java.util.List.of(a, b));
        mockFreshLocation();
        // 出的是 77001 那个产品的篮子
        when(locationStockMapper.sumPlotProductStock(FRESH_LOC_ID, 77001L, 11001L, SOURCE_BIZ_ID))
            .thenReturn(new BigDecimal("30.000"));
        org.dromara.djs.warehouse.stock.domain.LocationStock bk = basket(50001L, "30.000");
        bk.setProductId(77001L);
        when(locationStockMapper.selectPlotProductBaskets(FRESH_LOC_ID, 77001L, 11001L, SOURCE_BIZ_ID))
            .thenReturn(List.of(bk));
        when(locationStockMapper.deductStockById(anyLong(), any(BigDecimal.class), anyLong())).thenReturn(1);
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap())).thenReturn("F20260831OT0004");

        org.dromara.djs.warehouse.veg.domain.bo.ProcessSubmitBo bo = processBo(3, "10.000", 0);
        bo.setProductId(77001L);
        service.submitProcess(bo);

        ArgumentCaptor<org.dromara.djs.warehouse.veg.domain.FeedLog> cap =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.veg.domain.FeedLog.class);
        verify(feedLogMapper, times(1)).insert(cap.capture());
        // 回归防线：改回 resolveProductIdByCrop 就会变成 88001，有机饲喂记录的「产品名称」列随之退化成作物名
        assertThat(cap.getValue().getProductId()).isEqualTo(77001L);
        // 出库流水同样记在工人选的产品头上
        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        assertThat(flowCap.getValue().getProductId()).isEqualTo(77001L);
    }

    // -------- V6 row28 / row29：0 kg 收口（重量为 0 也能把地块推到完成）--------

    /**
     * 采摘录入 BO（V6 row28 四种组合共用）。
     *
     * @param weight      采摘重量
     * @param weighFinish 地块是否称重完成 1=是 / 0=否
     * @param teamIds     采摘班组（传 null 模拟「不选绩效组」）
     */
    private org.dromara.djs.warehouse.veg.domain.bo.HarvestSubmitBo harvestBo(
        String weight, int weighFinish, List<Long> teamIds) {
        org.dromara.djs.warehouse.veg.domain.bo.HarvestSubmitBo bo =
            new org.dromara.djs.warehouse.veg.domain.bo.HarvestSubmitBo();
        bo.setPlantingRecordId(60001L);
        bo.setHarvestWeight(new BigDecimal(weight));
        bo.setWeighFinish(weighFinish);
        bo.setWeighUserId(9001L);
        bo.setTeamIds(teamIds);
        return bo;
    }

    /** 未称重完成的汇总行（isWeighed=2），采摘录入的正常前置状态 */
    private VegetableHandle harvestHandle(String picked) {
        VegetableHandle h = new VegetableHandle();
        h.setId(70001L);
        h.setPlantingRecordId(60001L);
        h.setPlotId(11001L);
        h.setCropId(12001L);
        h.setPickedWeight(new BigDecimal(picked));
        h.setHandledWeight(BigDecimal.ZERO);
        h.setFeedWeight(BigDecimal.ZERO);
        h.setSendPlatformWeight(BigDecimal.ZERO);
        h.setStockInWeight(BigDecimal.ZERO);
        h.setLossWeight(BigDecimal.ZERO);
        h.setIsWeighed(2);
        h.setHandleStatus("processing");
        return h;
    }

    @Test
    @DisplayName("row28①：重量 0 + 称重完成=是 → 提交成功，落 0 kg 记录 + is_weighed 推 1，picked 不变")
    void testHarvest_ZeroWeight_WeighFinish_Ok() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));
        // weighFinish=1 的前置门：种植端该地块采摘已完成
        when(plantDetailsMapper.selectCount(any())).thenReturn(1L);

        Long handleId = service.submitHarvest(harvestBo("0", 1, List.of(30001L)));
        assertThat(handleId).isEqualTo(70001L);

        ArgumentCaptor<HandleRecord> recCap = ArgumentCaptor.forClass(HandleRecord.class);
        verify(recordMapper, times(1)).insert(recCap.capture());
        assertThat(recCap.getValue().getRecordWeight()).isEqualByComparingTo("0");
        assertThat(recCap.getValue().getIsWeighed()).isEqualTo(1);

        // 聚合：picked 加 0 仍是 50（不能变 NULL，否则汇总脏），is_weighed 推 1
        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        assertThat(updCap.getValue().getPickedWeight()).isEqualByComparingTo("50.000");
        assertThat(updCap.getValue().getIsWeighed()).isEqualTo(1);
    }

    // -------- V6 row102：采摘录入即入毛菜保鲜库 --------

    @Test
    @DisplayName("row102⑥：采摘录入即入毛菜保鲜库 —— 建地块篮 + veg_stock_in 入库流水，stock_in_weight 累加")
    void testHarvest_StocksIntoFreshVeg() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));
        mockCropProduct();
        mockFreshLocation();
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap())).thenReturn("F20260831IN0001");

        service.submitHarvest(harvestBo("30.000", 0, List.of(30001L)));

        // 一条入库流水（落 L0006、带地块、带产品）
        ArgumentCaptor<StockFlow> flowCap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(flowCap.capture());
        StockFlow flow = flowCap.getValue();
        assertThat(flow.getFlowType()).isEqualTo("veg_stock_in");
        assertThat(flow.getInoutType()).isEqualTo("IN");
        assertThat(flow.getWarehouseId()).isEqualTo(FRESH_LOC_ID);
        assertThat(flow.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(flow.getPlotId()).isEqualTo(11001L);
        assertThat(flow.getChangeNum()).isEqualByComparingTo("30.000");

        // 一个地块篮（后续处理录入就是从它里面 FIFO 出）
        ArgumentCaptor<org.dromara.djs.warehouse.stock.domain.LocationStock> basketCap =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.stock.domain.LocationStock.class);
        verify(locationStockMapper, times(1)).insert(basketCap.capture());
        org.dromara.djs.warehouse.stock.domain.LocationStock bk = basketCap.getValue();
        assertThat(bk.getLocationId()).isEqualTo(FRESH_LOC_ID);
        assertThat(bk.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(bk.getPlotId()).isEqualTo(11001L);
        assertThat(bk.getProductStock()).isEqualByComparingTo("30.000");
        assertThat(bk.getThirdPhase()).isZero();

        // stock_in_weight 语义 = 采摘累计入毛菜间的量
        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        assertThat(updCap.getValue().getPickedWeight()).isEqualByComparingTo("80.000");
        assertThat(updCap.getValue().getStockInWeight()).isEqualByComparingTo("30.000");
    }

    @Test
    @DisplayName("row102⑦：作物未配产出产品 → 采摘照常落库（不挡地头称重），只是不建库存篮、stock_in 不加")
    void testHarvest_NoCropProduct_RecordsButNoStock() {
        PlantingRecord planting = samplePlanting("processing");
        planting.setProductId(null);
        when(plantingRecordMapper.selectById(60001L)).thenReturn(planting);
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));
        // 作物既没有产品配置、也没有 related_product
        CropInfo crop = new CropInfo();
        crop.setId(12001L);
        crop.setRelatedProduct(null);
        when(cropInfoMapper.selectById(12001L)).thenReturn(crop);

        Long handleId = service.submitHarvest(harvestBo("30.000", 0, List.of(30001L)));
        assertThat(handleId).isEqualTo(70001L);

        // 采摘记录 + 聚合照常落库
        verify(recordMapper, times(1)).insert(any(HandleRecord.class));
        // 但不写流水、不建篮
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never())
            .insert(any(org.dromara.djs.warehouse.stock.domain.LocationStock.class));

        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        assertThat(updCap.getValue().getPickedWeight()).isEqualByComparingTo("80.000");
        assertThat(updCap.getValue().getStockInWeight()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("row102⑧：0 kg 收口的采摘记录不建空篮、不写 0 kg 入库流水")
    void testHarvest_ZeroWeight_NoStockSideEffect() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));
        when(plantDetailsMapper.selectCount(any())).thenReturn(1L);
        mockCropProduct();
        mockFreshLocation();

        service.submitHarvest(harvestBo("0", 1, List.of(30001L)));

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never())
            .insert(any(org.dromara.djs.warehouse.stock.domain.LocationStock.class));
        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        assertThat(updCap.getValue().getStockInWeight()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("row28②：重量 0 + 称重完成=否 → 抛「必须打开地块是否称重完成」，不落库")
    void testHarvest_ZeroWeight_NoFinish_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));

        assertThatThrownBy(() -> service.submitHarvest(harvestBo("0", 0, List.of(30001L))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("必须打开「地块是否称重完成」");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("row28③：重量 0 + 称重完成=是 + 不选绩效组 → 提交成功，team_id 留空、中间表不写行")
    void testHarvest_ZeroWeight_WeighFinish_NoTeam_Ok() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));
        when(plantDetailsMapper.selectCount(any())).thenReturn(1L);

        // teamIds=null 模拟 mp 完全不传班组字段（比空数组更极端，服务端不得 NPE）
        Long handleId = service.submitHarvest(harvestBo("0", 1, null));
        assertThat(handleId).isEqualTo(70001L);

        ArgumentCaptor<HandleRecord> recCap = ArgumentCaptor.forClass(HandleRecord.class);
        verify(recordMapper, times(1)).insert(recCap.capture());
        assertThat(recCap.getValue().getTeamId()).isNull();
        // 班组多选中间表一行都不写（0 kg 不进 row39 绩效聚合）
        verify(recordTeamMapper, never())
            .insert(any(org.dromara.djs.warehouse.veg.domain.HandleRecordTeam.class));
    }

    @Test
    @DisplayName("row28④：重量 > 0 + 不选绩效组 → 仍抛「请选择采摘班组」，不落库（原口径不放宽）")
    void testHarvest_PositiveWeight_NoTeam_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));
        when(plantDetailsMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.submitHarvest(harvestBo("12.500", 0, List.of())))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("请选择采摘班组");
        // 连 null 也一样拦
        assertThatThrownBy(() -> service.submitHarvest(harvestBo("12.500", 1, null)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("请选择采摘班组");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("row29①：处理重量 0 + 处理完成=是 → 收口成功（done），不写 0 kg 的出库流水 / 饲料台账")
    void testProcess_ZeroWeight_ProcessFinish_Ok() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        // picked=50 已全部处理完（handled=50），现场只差点「处理完成」→ 0 kg 收口
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "50.000", 1));
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(1);
        mockCropProduct();
        mockFreshLocation();
        // 毛菜间已经出干净（一篮不剩）→ 收口时无货可转损耗

        // 去向③饲料：0 kg 不得写 feed_log
        service.submitProcess(processBo(3, "0", 1));

        ArgumentCaptor<HandleRecord> recCap = ArgumentCaptor.forClass(HandleRecord.class);
        verify(recordMapper, times(1)).insert(recCap.capture());
        assertThat(recCap.getValue().getRecordWeight()).isEqualByComparingTo("0");
        assertThat(recCap.getValue().getIsFinish()).isEqualTo(1);

        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        VegetableHandle upd = updCap.getValue();
        // 各重量加 0 后原值不变（不得变 NULL）
        assertThat(upd.getHandledWeight()).isEqualByComparingTo("50.000");
        assertThat(upd.getFeedWeight()).isEqualByComparingTo("0");
        assertThat(upd.getIsFinish()).isEqualTo(1);
        assertThat(upd.getHandleStatus()).isEqualTo("done");

        verify(feedLogMapper, never()).insert(any(org.dromara.djs.warehouse.veg.domain.FeedLog.class));
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        // 毛菜间已空 → 收口不结损耗、不写 loss_flow
        assertThat(upd.getLossWeight()).isEqualByComparingTo("0");
        verify(lossFlowService, never()).record(any(org.dromara.djs.warehouse.loss.domain.LossFlow.class));
        verify(plantingRecordMapper).advanceHandleStatus(eq(60001L), eq("processing"), eq("done"), eq(9001L));
    }

    @Test
    @DisplayName("row29②：处理重量 0 + 处理完成=否 → 抛「必须打开地块是否处理完成」，不落库")
    void testProcess_ZeroWeight_NoFinish_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "10.000", 1));

        assertThatThrownBy(() -> service.submitProcess(processBo(2, "0", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("必须打开「地块是否处理完成」");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    // -------- V6 row41：0 kg 收口时去向不必填（有货仍必填）--------

    @Test
    @DisplayName("row41①：处理重量 0 + 处理完成=是 + 不选去向 → 收口成功，handle_target 落 NULL，重量桶都不动")
    void testProcess_ZeroWeight_NoTarget_Ok() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        // picked=50 已全部处理完（handled=50），只差点「处理完成」→ 0 kg 收口且懒得选去向
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "50.000", 1));
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(1);
        mockCropProduct();
        mockFreshLocation();

        service.submitProcess(processBo(null, "0", 1));

        ArgumentCaptor<HandleRecord> recCap = ArgumentCaptor.forClass(HandleRecord.class);
        verify(recordMapper, times(1)).insert(recCap.capture());
        assertThat(recCap.getValue().getRecordWeight()).isEqualByComparingTo("0");
        assertThat(recCap.getValue().getHandleTarget()).isNull();
        assertThat(recCap.getValue().getIsFinish()).isEqualTo(1);

        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        VegetableHandle upd = updCap.getValue();
        // 未选去向 → 三个桶一个都不许动（也不得变 NULL）
        assertThat(upd.getStockInWeight()).isEqualByComparingTo("50.000");
        assertThat(upd.getSendPlatformWeight()).isEqualByComparingTo("0");
        assertThat(upd.getFeedWeight()).isEqualByComparingTo("0");
        assertThat(upd.getHandledWeight()).isEqualByComparingTo("50.000");
        assertThat(upd.getIsFinish()).isEqualTo(1);
        assertThat(upd.getHandleStatus()).isEqualTo("done");

        // 0 kg 不写任何下游台账
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(feedLogMapper, never()).insert(any(org.dromara.djs.warehouse.veg.domain.FeedLog.class));
        verify(plantingRecordMapper).advanceHandleStatus(eq(60001L), eq("processing"), eq("done"), eq(9001L));
    }

    @Test
    @DisplayName("row41②：0 kg + 不选去向 → 毛菜间剩余照常结转损耗并写 loss_flow（收口不得短路损耗）")
    void testProcess_ZeroWeight_NoTarget_StillSettlesLoss() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "30.000", 1));
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(1);
        mockCropProduct();
        mockFreshLocation();
        // 毛菜间还剩 20kg 没处理，收口时全部记损耗
        mockFreshBaskets("20.000");

        service.submitProcess(processBo(null, "0", 1));

        // 篮子必须被扣到 0（只写损耗不扣库存 = 已损耗的量永远挂在库里）
        verify(locationStockMapper, times(1))
            .deductStockById(eq(50001L), any(BigDecimal.class), eq(9001L));

        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        assertThat(updCap.getValue().getLossWeight()).isEqualByComparingTo("20.000");

        ArgumentCaptor<org.dromara.djs.warehouse.loss.domain.LossFlow> lossCap =
            ArgumentCaptor.forClass(org.dromara.djs.warehouse.loss.domain.LossFlow.class);
        verify(lossFlowService, times(1)).record(lossCap.capture());
        assertThat(lossCap.getValue().getLossType()).isEqualTo("veg_handle_loss");
        assertThat(lossCap.getValue().getLossWeight()).isEqualByComparingTo("20.000");
    }

    @Test
    @DisplayName("row41③：重量 > 0 + 不选去向 → 抛「请选择去向」，不落库（有货必须说清楚去哪）")
    void testProcess_PositiveWeight_NoTarget_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "10.000", 1));

        assertThatThrownBy(() -> service.submitProcess(processBo(null, "10.000", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("请选择去向");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("row41④：0 kg + 不选去向 + 处理完成=否 → 仍抛「必须打开地块是否处理完成」（row29 口径不放宽）")
    void testProcess_ZeroWeight_NoTarget_NoFinish_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "10.000", 1));

        assertThatThrownBy(() -> service.submitProcess(processBo(null, "0", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("必须打开「地块是否处理完成」");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("row41⑤：去向传了非法值（0 kg 也一样）→ 抛「处理目标非法」，不落库")
    void testProcess_IllegalTarget_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "10.000", 1));

        assertThatThrownBy(() -> service.submitProcess(processBo(9, "0", 1)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("处理目标非法");
        assertThatThrownBy(() -> service.submitProcess(processBo(9, "5.000", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("处理目标非法");

        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    // -------- 采摘明细只读列表（FIX-ADMIN-0721） --------

    @Test
    @DisplayName("采摘明细 happy：分页查询透传 mapper 固定行，query 原样下传")
    void testQueryPickDetailPage_HappyPath() {
        PickDetailVo row = new PickDetailVo();
        row.setPickDate(LocalDate.of(2026, 7, 21));
        row.setCropName("小白菜");
        row.setPlotCode("A-01");
        row.setPickWeight(new BigDecimal("12.345"));
        row.setTeamId(30001L);
        row.setTeamName("采摘一组");
        Page<PickDetailVo> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(recordMapper.selectPickDetailPage(any(), any(PickDetailQuery.class))).thenReturn(page);

        PickDetailQuery q = new PickDetailQuery();
        q.setCropName("白菜");
        q.setTeamId(30001L);
        TableDataInfo<PickDetailVo> result = service.queryPickDetailPage(q, new PageQuery(1, 10));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).hasSize(1);
        assertThat(result.getRows().get(0).getCropName()).isEqualTo("小白菜");
        assertThat(result.getRows().get(0).getPlotCode()).isEqualTo("A-01");
        assertThat(result.getRows().get(0).getPickWeight()).isEqualByComparingTo("12.345");
        verify(recordMapper).selectPickDetailPage(any(), eq(q));
    }

    @Test
    @DisplayName("row105：绩效百分比 + 备注与称重记录同行落库（甲方第 4 条）")
    void testHarvest_PerfPercentAndRemark_PersistedOnSameRow() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));

        org.dromara.djs.warehouse.veg.domain.bo.HarvestSubmitBo bo = harvestBo("100.000", 0, List.of(30001L));
        bo.setPerfPercent(80);
        bo.setRemark("雨天减产");
        service.submitHarvest(bo);

        ArgumentCaptor<HandleRecord> recCap = ArgumentCaptor.forClass(HandleRecord.class);
        verify(recordMapper, times(1)).insert(recCap.capture());
        HandleRecord rec = recCap.getValue();
        assertThat(rec.getPerfPercent()).isEqualTo(80);
        assertThat(rec.getRemark()).isEqualTo("雨天减产");
        assertThat(rec.getRecordWeight()).isEqualByComparingTo("100.000");
    }

    @Test
    @DisplayName("row105：漏传绩效百分比 → 服务端补 100（不落 null，绩效聚合按全额计）")
    void testHarvest_PerfPercentMissing_DefaultsTo100() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));

        service.submitHarvest(harvestBo("100.000", 0, List.of(30001L)));

        ArgumentCaptor<HandleRecord> recCap = ArgumentCaptor.forClass(HandleRecord.class);
        verify(recordMapper, times(1)).insert(recCap.capture());
        assertThat(recCap.getValue().getPerfPercent()).isEqualTo(100);
    }

    // -------- error path / 并发 CAS（被保护的生产代码原封未动，用例改挂到新入口上）--------

    @Test
    @DisplayName("缺 L0006 库位：处理录入 fail-fast，一行不落（requireFreshVegLocationId 在扣库存 / 落明细之前）")
    void testProcess_MissingFreshLocation_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "0", 1));
        mockCropProduct();
        // 故意不 mockFreshLocation() —— 库位主数据里没维护 L0006

        assertThatThrownBy(() -> service.submitProcess(processBo(2, "10.000", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("毛菜鲜品库")
            .hasMessageContaining("L0006");

        // 处理录入路径上这道门在 INSERT 之前，所以是真正的「一行不落」
        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(locationStockMapper, never()).deductStockById(anyLong(), any(BigDecimal.class), anyLong());
    }

    @Test
    @DisplayName("缺 L0006 库位：采摘录入同样 fail-fast，不建库存篮 / 不写入库流水 / 汇总不推进")
    void testHarvest_MissingFreshLocation_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(harvestHandle("50.000"));
        mockCropProduct();
        // 故意不 mockFreshLocation()

        assertThatThrownBy(() -> service.submitHarvest(harvestBo("30.000", 0, List.of(30001L))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("毛菜鲜品库")
            .hasMessageContaining("L0006");

        // 采摘侧这道门在 handle_record INSERT 之后（Step 3.2），所以断言的是「库存副作用一个都没有」，
        // 明细行由 @Transactional 整笔回滚兜底 —— 不为了让断言好看去挪生产代码的校验位置。
        verify(locationStockMapper, never())
            .insert(any(org.dromara.djs.warehouse.stock.domain.LocationStock.class));
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
    }

    @Test
    @DisplayName("planting_record 已 done → 采摘 / 处理两个入口都抛「已处理完成，不能再录入」，不落库")
    void testPlantingDone_Rejected() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("done"));

        assertThatThrownBy(() -> service.submitHarvest(harvestBo("10.000", 0, List.of(30001L))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("该种植记录已处理完成，不能再录入");
        assertThatThrownBy(() -> service.submitProcess(processBo(2, "10.000", 0)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("该种植记录已处理完成，不能再录入");

        verify(handleMapper, never()).insert(any(VegetableHandle.class));
        verify(handleMapper, never()).updateById(any(VegetableHandle.class));
        verify(recordMapper, never()).insert(any(HandleRecord.class));
        verify(locationStockMapper, never()).deductStockById(anyLong(), any(BigDecimal.class), anyLong());
    }

    @Test
    @DisplayName("并发 CAS：采摘时 advanceHandleStatus pending→processing 返 0（已被并发工人推进），主事务不崩")
    void testHarvest_ConcurrentAdvanceReturnZero_NoCrash() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("pending"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(null);
        when(handleMapper.insert(any(VegetableHandle.class))).thenAnswer(inv -> {
            inv.getArgument(0, VegetableHandle.class).setId(70001L);
            return 1;
        });
        mockCropProduct();
        mockFreshLocation();
        when(bizCodeGenerator.generate(eq(BizCodeType.STOCK_FLOW_NO), anyMap())).thenReturn("F20260831IN0100");
        // 模拟并发：另一个工人 0.001s 前已推进 pending→processing，本事务 CAS 返 0
        when(plantingRecordMapper.advanceHandleStatus(eq(60001L), eq("pending"), eq("processing"), eq(9001L)))
            .thenReturn(0);

        // 不应抛 —— CAS 返 0 只说明状态已被并发推进；主事务（采收明细 + 建篮 + 聚合）落库才是核心语义
        Long handleId = service.submitHarvest(harvestBo("50.000", 0, List.of(30001L)));
        assertThat(handleId).isEqualTo(70001L);

        verify(recordMapper, times(1)).insert(any(HandleRecord.class));
        verify(handleMapper, times(1)).updateById(any(VegetableHandle.class));
        verify(locationStockMapper, times(1))
            .insert(any(org.dromara.djs.warehouse.stock.domain.LocationStock.class));
        verify(plantingRecordMapper, times(1)).advanceHandleStatus(
            eq(60001L), eq("pending"), eq("processing"), eq(9001L));
    }

    @Test
    @DisplayName("并发终态：处理完成时 advanceHandleStatus processing→done 返 0（已被推完），主事务不崩")
    void testProcess_ConcurrentFinishAdvanceReturnZero_NoCrash() {
        when(plantingRecordMapper.selectById(60001L)).thenReturn(samplePlanting("processing"));
        when(handleMapper.selectByPlantingRecordId(60001L)).thenReturn(sampleHandle("50.000", "50.000", 1));
        mockCropProduct();
        mockFreshLocation();
        // 并发：另一个 finish 提交 0.001s 前已把 planting_record 推到 done
        when(plantingRecordMapper.advanceHandleStatus(anyLong(), any(), any(), anyLong())).thenReturn(0);

        // 不抛：CAS 失败被吸收，主事务（vegetable_handle.done + 明细 + 聚合）仍落库
        service.submitProcess(processBo(3, "0", 1));

        ArgumentCaptor<VegetableHandle> updCap = ArgumentCaptor.forClass(VegetableHandle.class);
        verify(handleMapper, times(1)).updateById(updCap.capture());
        assertThat(updCap.getValue().getIsFinish()).isEqualTo(1);
        assertThat(updCap.getValue().getHandleStatus()).isEqualTo("done");
        verify(recordMapper, times(1)).insert(any(HandleRecord.class));
        verify(plantingRecordMapper).advanceHandleStatus(eq(60001L), eq("processing"), eq("done"), eq(9001L));
    }

}
