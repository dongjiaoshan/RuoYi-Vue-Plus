package org.dromara.djs.store.ledger.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.DictService;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.store.domain.Store;
import org.dromara.djs.common.store.mapper.StoreMapper;
import org.dromara.djs.common.store.service.IStoreService;
import org.dromara.djs.store.inventory.domain.StoreInventory;
import org.dromara.djs.store.inventory.mapper.StoreInventoryMapper;
import org.dromara.djs.store.ledger.domain.StoreDailyLedger;
import org.dromara.djs.store.ledger.domain.bo.StoreDailyLedgerBatchBo;
import org.dromara.djs.store.ledger.domain.vo.StoreDailyLedgerCandidateVo;
import org.dromara.djs.store.ledger.mapper.StoreDailyLedgerMapper;
import org.dromara.djs.store.operation.domain.StoreSaleRecord;
import org.dromara.djs.store.operation.mapper.StoreSaleRecordMapper;
import org.dromara.djs.store.returns.domain.StoreReturn;
import org.dromara.djs.store.returns.mapper.StoreReturnMapper;
import org.dromara.djs.warehouse.demand.core.DemandArrivedQuantityFiller;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.shipment.domain.Shipment;
import org.dromara.djs.warehouse.shipment.mapper.ShipmentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreDailyLedgerServiceImpl} 单测。
 *
 * <p>覆盖猪肉盘点候选口径：</p>
 * <ol>
 *   <li>候选唯一来源 = 字典 {@code djs_white_bar_return_product}（白条到店分割部位），
 *       未对上产品的占位业务码不产生行；</li>
 *   <li>无「当日白条到店」门禁——当天没有到货也照样列出猪肉产品行（盘的是门店现有原材料）；</li>
 *   <li>提交时猪肉行入库量累计仍受当日白条发货重量上限约束（无到货 → 上限 0，入库>0 拒绝、期末量可正常录）。</li>
 * </ol>
 *
 * @author djs
 * @since STORE-LEDGER-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreDailyLedgerServiceImpl 单元测试")
class StoreDailyLedgerServiceImplTest {

    @Mock private StoreDailyLedgerMapper baseMapper;
    @Mock private StoreMapper storeMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private StoreSaleRecordMapper saleRecordMapper;
    @Mock private StoreReturnMapper storeReturnMapper;
    @Mock private ShipmentMapper shipmentMapper;
    @Mock private DemandManageMapper demandManageMapper;
    @Mock private ProductProductionMapper productProductionMapper;
    @Mock private DemandArrivedQuantityFiller arrivedQuantityFiller;
    @Mock private StoreInventoryMapper storeInventoryMapper;
    @Mock private DictService dictService;
    @Mock private IStoreService storeService;
    @Mock private org.dromara.djs.store.trace.service.IStoreTraceService storeTraceService;

    private StoreDailyLedgerServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    private static final String DICT_WHITE_BAR_RETURN_PRODUCT = "djs_white_bar_return_product";
    private static final Long STORE_ID = 5001L;
    private static final Long USER_ID = 2001L;
    /** 字典项①：业务码对得上产品（真实原材料部位）。 */
    private static final Long PORK_PRODUCT_ID = 9303000000000101L;
    private static final String PORK_PRODUCT_CODE = "Y00101";
    /** 字典项②：甲方未给产品码的占位 value（命中不到产品，预期不进候选）。 */
    private static final String PLACEHOLDER_CODE = "rou_mo";
    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);

    /** MyBatis-Plus 单测 entity cache 预热（LambdaQueryWrapper 解析列名需要）。 */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, StoreDailyLedger.class);
        TableInfoHelper.initTableInfo(assistant, StoreInventory.class);
        TableInfoHelper.initTableInfo(assistant, StoreSaleRecord.class);
        TableInfoHelper.initTableInfo(assistant, StoreReturn.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, Shipment.class);
        TableInfoHelper.initTableInfo(assistant, DemandManage.class);
    }

    @BeforeEach
    void setup() {
        service = new StoreDailyLedgerServiceImpl(baseMapper, storeMapper, productInfoMapper,
            saleRecordMapper, storeReturnMapper, shipmentMapper, demandManageMapper,
            productProductionMapper, arrivedQuantityFiller, storeInventoryMapper, dictService, storeService,
            storeTraceService);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);
        // 默认「当日没有现场打包」——要验销售量取追溯码消耗量的用例各自覆盖它
        lenient().when(storeTraceService.sumOnsiteConsumedWeightByMaterial(any(), any())).thenReturn(Map.of());

        Store store = new Store();
        store.setId(STORE_ID);
        store.setStoreName("测试门店");
        when(storeMapper.selectById(STORE_ID)).thenReturn(store);
        // 字典 = 甲方「白条到店分割类型」：真实业务码 + 占位码各一项（key=dict_value）。
        Map<String, String> dict = new LinkedHashMap<>();
        dict.put(PORK_PRODUCT_CODE, "五花肉");
        dict.put(PLACEHOLDER_CODE, "肉末");
        when(dictService.getAllDictByDictType(DICT_WHITE_BAR_RETURN_PRODUCT)).thenReturn(dict);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    @Test
    @DisplayName("listCandidates：猪肉候选取白条分割部位字典，当日无白条到店也照常列出；占位业务码不成行")
    void testListCandidates_PorkFromDict_NoArrivalGate() {
        // selectList 依次：① 字典业务码 → 产品（占位码无产品，仅 1 条）；② 材料外售 swap（无）；③ 展示产品明细。
        when(productInfoMapper.selectList(any())).thenReturn(
            List.of(porkProduct()), List.of(), List.of(porkProduct()));
        // 当日无发货、无库存、无流水 → 只剩字典驱动的猪肉候选。
        when(shipmentMapper.selectList(any())).thenReturn(List.of());
        when(storeInventoryMapper.selectList(any())).thenReturn(List.of());
        when(saleRecordMapper.selectList(any())).thenReturn(List.of());
        when(storeReturnMapper.selectList(any())).thenReturn(List.of());

        List<StoreDailyLedgerCandidateVo> candidates = service.listCandidates(STORE_ID, DATE);

        assertThat(candidates).hasSize(1);
        StoreDailyLedgerCandidateVo vo = candidates.get(0);
        assertThat(vo.getProductId()).isEqualTo(PORK_PRODUCT_ID);
        assertThat(vo.getCategory()).isEqualTo("pork");
        assertThat(vo.getBelongTab()).isEqualTo("pork");
        // 猪肉行入库量可手动录入；当日无到货 → 预填 0，期初/流水量均 0。
        assertThat(vo.getInboundReadonly()).isFalse();
        assertThat(vo.getInboundQty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getOpeningQty()).isEqualByComparingTo(BigDecimal.ZERO);
        // 不再探查「当日是否有白条到店」（无发货则需求表根本不参与取数）。
        verify(demandManageMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("D-0047：新到货行「当日入库」取到店量，不取需求量（部分到店时不再虚增入库与损耗）")
    void testListCandidates_InboundTakesArrivedQuantity() {
        Long vegProductId = 9304000000000042L;
        Long demandId = 2096798706305671170L;

        ProductInfo veg = new ProductInfo();
        veg.setId(vegProductId);
        veg.setProductName("有机紫线茄500g");
        veg.setProductUnit("份");
        veg.setBelongType("vegetable");
        // 字典置空 → 猪肉候选不打库。productInfoMapper.selectList 依次：① 材料外售 swap（无）；② 展示产品明细。
        when(dictService.getAllDictByDictType(DICT_WHITE_BAR_RETURN_PRODUCT)).thenReturn(new LinkedHashMap<>());
        when(productInfoMapper.selectList(any())).thenReturn(List.of(), List.of(veg));

        Shipment shipment = new Shipment();
        shipment.setDemandId(demandId);
        shipment.setShipmentNo("S202609080001");
        when(shipmentMapper.selectList(any())).thenReturn(List.of(shipment));

        DemandManage demand = new DemandManage();
        demand.setId(demandId);
        demand.setProductId(vegProductId);
        demand.setDemandQuantity(new BigDecimal("3.000"));    // 订购 3 份
        when(demandManageMapper.selectList(any())).thenReturn(List.of(demand));
        // 实际只发/清点了 1 份 → 需求下单页显示「部分到店 · 到店量 1」
        when(arrivedQuantityFiller.resolve(List.of(demandId)))
            .thenReturn(Map.of(demandId, new BigDecimal("1.000")));

        when(storeInventoryMapper.selectList(any())).thenReturn(List.of());
        when(saleRecordMapper.selectList(any())).thenReturn(List.of());
        when(storeReturnMapper.selectList(any())).thenReturn(List.of());

        List<StoreDailyLedgerCandidateVo> candidates = service.listCandidates(STORE_ID, DATE);

        assertThat(candidates).hasSize(1);
        StoreDailyLedgerCandidateVo vo = candidates.get(0);
        assertThat(vo.getCategory()).isEqualTo("inbound");
        // 甲方原话「当日入库的数据现在需要取到店量的数据，不以需求量进行获取」
        assertThat(vo.getInboundQty()).isEqualByComparingTo("1.000");
        assertThat(vo.getInboundQty()).as("取需求量 3 会让期末 0 时的损耗虚增成 3").isNotEqualByComparingTo("3.000");
        // 新到货行入库只读（工人改不了），值必须由后端一次算对
        assertThat(vo.getInboundReadonly()).isTrue();
    }

    @Test
    @DisplayName("batchSave：当日无白条发货（上限 0）时猪肉行入库量 > 0 → 拒绝")
    void testBatchSave_PorkInboundOverLimit_Rejected() {
        when(baseMapper.selectList(any())).thenReturn(List.of());
        // selectList 依次：① 上限适用集字典产品；② 材料外售上限的字典产品；③ 材料外售成品（无）。
        when(productInfoMapper.selectList(any())).thenReturn(
            List.of(porkProduct()), List.of(porkProduct()), List.of());
        when(shipmentMapper.selectList(any())).thenReturn(List.of());
        when(productInfoMapper.selectById(PORK_PRODUCT_ID)).thenReturn(porkProduct());

        StoreDailyLedgerBatchBo bo = batchBo(new BigDecimal("3.000"), new BigDecimal("3.000"));

        assertThatThrownBy(() -> service.batchSave(bo))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("不能超过");
        verify(baseMapper, never()).insert(any(StoreDailyLedger.class));
    }

    @Test
    @DisplayName("V6-R215：猪肉原材料行期末+损耗手填 → **退回量倒算**，落台账 + 回写门店库存")
    void testBatchSave_PorkClosingOnly_Saved() {
        when(baseMapper.selectList(any())).thenReturn(List.of());
        when(productInfoMapper.selectList(any())).thenReturn(
            List.of(porkProduct()), List.of(porkProduct()), List.of());
        when(shipmentMapper.selectList(any())).thenReturn(List.of());
        when(productInfoMapper.selectById(PORK_PRODUCT_ID)).thenReturn(porkProduct());
        when(baseMapper.insert(any(StoreDailyLedger.class))).thenReturn(1);
        when(storeInventoryMapper.selectOne(any())).thenReturn(null);
        when(storeInventoryMapper.insert(any(StoreInventory.class))).thenReturn(1);

        int saved = service.batchSave(batchBo(BigDecimal.ZERO, new BigDecimal("2.500")));

        assertThat(saved).isEqualTo(1);
        ArgumentCaptor<StoreDailyLedger> cap = ArgumentCaptor.forClass(StoreDailyLedger.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        StoreDailyLedger row = cap.getValue();
        assertThat(row.getClosingQty()).isEqualByComparingTo("2.500");
        // R215：损耗改手填（BO 没给 → 默认 0），退回量成了倒算项
        // 退回 = 期初 + 入库 − 销售 − 赠送 − 期末 − 损耗 = 5 + 0 − 0 − 0 − 2.5 − 0 = 2.5
        assertThat(row.getLossQty()).as("猪肉原材料行的损耗是手填值，未填即 0").isEqualByComparingTo("0");
        assertThat(row.getWhReturnQty()).as("猪肉原材料行的退回量由公式倒算").isEqualByComparingTo("2.500");
        ArgumentCaptor<StoreInventory> invCap = ArgumentCaptor.forClass(StoreInventory.class);
        verify(storeInventoryMapper, times(1)).insert(invCap.capture());
        assertThat(invCap.getValue().getStockQty()).isEqualByComparingTo("2.500");
    }


    @Test
    @DisplayName("V6-R215：猪肉原材料行的销售量取**现场打包追溯码消耗量**，不再取销售流水")
    void testListCandidates_PorkMaterialSaleFromTraceConsumption() {
        when(dictService.getAllDictByDictType(DICT_WHITE_BAR_RETURN_PRODUCT))
            .thenReturn(new LinkedHashMap<>(Map.of(PORK_PRODUCT_CODE, "五花肉")));
        when(productInfoMapper.selectList(any())).thenReturn(
            List.of(porkProduct()), List.of(), List.of(porkProduct()));
        when(shipmentMapper.selectList(any())).thenReturn(List.of());
        when(storeInventoryMapper.selectList(any())).thenReturn(List.of());
        // 销售流水里有 9 —— 新口径下它**不该**被采用
        StoreSaleRecord sale = new StoreSaleRecord();
        sale.setProductId(PORK_PRODUCT_ID);
        sale.setSaleQty(new BigDecimal("9.000"));
        when(saleRecordMapper.selectList(any())).thenReturn(List.of(sale));
        when(storeReturnMapper.selectList(any())).thenReturn(List.of());
        // ⚠️ key 必须是**原材料 id**，不是产品名。
        // 血泪（clean-QA 2026-09-14）：上一版这里 stub 成 Map.of("五花肉", …)，正好把实现里的 bug 前提钉死 ——
        // 真实 remark 里的「部位」是打包**成品名**（黑毛猪五花肉250g/份），与原材料名零交集，
        // 线上恒取 0，而这条用例照样绿。mock 的假设必须与被测契约一致，否则断言写得再硬也是假绿。
        when(storeTraceService.sumOnsiteConsumedWeightByMaterial(eq(STORE_ID), any()))
            .thenReturn(Map.of(PORK_PRODUCT_ID, new BigDecimal("1.500")));

        List<StoreDailyLedgerCandidateVo> rows = service.listCandidates(STORE_ID, DATE);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getPorkMaterialRow()).isTrue();
        assertThat(rows.get(0).getSaleQty())
            .as("销售量必须取追溯码消耗量 1.5，取到销售流水的 9 就是没换源")
            .isEqualByComparingTo("1.500");
    }

    @Test
    @DisplayName("V6-R215：猪肉**生产产品**（product_attr=1）不走新口径 —— 甲方「生产产品逻辑不变」")
    void testBatchSave_PorkFinishedProductKeepsOldFormula() {
        when(baseMapper.selectList(any())).thenReturn(List.of());
        when(productInfoMapper.selectList(any())).thenReturn(
            List.of(porkFinishedProduct()), List.of(porkFinishedProduct()), List.of());
        when(shipmentMapper.selectList(any())).thenReturn(List.of());
        when(productInfoMapper.selectById(PORK_PRODUCT_ID)).thenReturn(porkFinishedProduct());
        when(baseMapper.insert(any(StoreDailyLedger.class))).thenReturn(1);
        when(storeInventoryMapper.selectOne(any())).thenReturn(null);
        when(storeInventoryMapper.insert(any(StoreInventory.class))).thenReturn(1);

        service.batchSave(batchBo(BigDecimal.ZERO, new BigDecimal("2.500")));

        ArgumentCaptor<StoreDailyLedger> cap = ArgumentCaptor.forClass(StoreDailyLedger.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        StoreDailyLedger row = cap.getValue();
        // 老口径：期末手填、损耗倒算 = 5 + 0 − 0 − 0 + 0 − 0 − 2.5
        assertThat(row.getLossQty()).isEqualByComparingTo("2.500");
        assertThat(row.getWhReturnQty()).as("生产产品行的退回量仍来自退回模块聚合，不倒算").isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("V6-R215：猪肉原材料行填了损耗 → 退回量随之减少（倒算式逐字对齐甲方给的公式）")
    void testBatchSave_PorkMaterialLossReducesDerivedReturn() {
        when(baseMapper.selectList(any())).thenReturn(List.of());
        when(productInfoMapper.selectList(any())).thenReturn(
            List.of(porkProduct()), List.of(porkProduct()), List.of());
        when(shipmentMapper.selectList(any())).thenReturn(List.of());
        when(productInfoMapper.selectById(PORK_PRODUCT_ID)).thenReturn(porkProduct());
        when(baseMapper.insert(any(StoreDailyLedger.class))).thenReturn(1);
        when(storeInventoryMapper.selectOne(any())).thenReturn(null);
        when(storeInventoryMapper.insert(any(StoreInventory.class))).thenReturn(1);

        // 入库留 0：白条发货上限闸是另一条既有规则（testBatchSave_PorkInboundOverLimit_Rejected 在管），
        // 本用例只验倒算式，不去踩那道闸。
        StoreDailyLedgerBatchBo bo = batchBo(BigDecimal.ZERO, new BigDecimal("1.000"));
        StoreDailyLedgerBatchBo.Item item = bo.getItems().get(0);
        item.setSaleQty(new BigDecimal("2.000"));
        item.setGiftQty(new BigDecimal("0.500"));
        item.setLossQty(new BigDecimal("0.250"));
        // 顾客退货给个非 0 值：甲方给的式子里没有这一项，这里钉住「确实没被算进去」
        item.setReturnSaleQty(new BigDecimal("7.000"));

        service.batchSave(bo);

        ArgumentCaptor<StoreDailyLedger> cap = ArgumentCaptor.forClass(StoreDailyLedger.class);
        verify(baseMapper, times(1)).insert(cap.capture());
        StoreDailyLedger row = cap.getValue();
        assertThat(row.getLossQty()).isEqualByComparingTo("0.250");
        // 退回 = 期初 5 + 入库 0 − 销售 2 − 赠送 0.5 − 期末 1 − 损耗 0.25 = 1.25
        //（顾客退货 7 **不入式**，甲方给的公式里没有这一项 —— 这是本用例要钉的点）
        assertThat(row.getWhReturnQty()).isEqualByComparingTo("1.250");
        assertThat(row.getReturnQty()).as("顾客退货仍原样落库，只是不进倒算式").isEqualByComparingTo("7.000");
    }

    /**
     * 字典业务码对得上的猪肉**原材料**产品（按重量盘点，未配上级原材料）。
     * {@code productAttr=2} 与生产数据一致 —— staging 实查：白条分割部位字典那 17 个码全部是原材料，
     * 所以这个夹具走的是 V6-R215 的新口径分支。
     */
    private ProductInfo porkProduct() {
        ProductInfo p = new ProductInfo();
        p.setId(PORK_PRODUCT_ID);
        p.setProductId(PORK_PRODUCT_CODE);
        p.setProductName("五花肉");
        p.setProductUnit("kg");
        p.setBelongType("pork");
        p.setProductAttr(2);
        p.setIsMaterialSold(0);
        return p;
    }

    /** 猪肉**生产产品**（product_attr=1）—— 甲方明说「生产产品逻辑不变」，用它钉老口径没被带歪。 */
    private ProductInfo porkFinishedProduct() {
        ProductInfo p = porkProduct();
        p.setProductAttr(1);
        p.setProductName("黑毛猪五花肉500g");
        p.setProductUnit("份");
        return p;
    }

    /** 单行盘点提交（期初 5.000 固定，入库 / 期末由用例给）。 */
    private StoreDailyLedgerBatchBo batchBo(BigDecimal inbound, BigDecimal closing) {
        StoreDailyLedgerBatchBo.Item item = new StoreDailyLedgerBatchBo.Item();
        item.setProductId(PORK_PRODUCT_ID);
        item.setOpeningQty(new BigDecimal("5.000"));
        item.setInboundQty(inbound);
        item.setClosingQty(closing);
        StoreDailyLedgerBatchBo bo = new StoreDailyLedgerBatchBo();
        bo.setStoreId(STORE_ID);
        bo.setLedgerDate(DATE);
        bo.setItems(List.of(item));
        return bo;
    }
}
