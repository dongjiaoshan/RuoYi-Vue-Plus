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
    @Mock private StoreInventoryMapper storeInventoryMapper;
    @Mock private DictService dictService;
    @Mock private IStoreService storeService;

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
            productProductionMapper, storeInventoryMapper, dictService, storeService);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);

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
    @DisplayName("batchSave：当日无白条发货时入库量 0 的猪肉行仍可录期末量 → 落台账 + 回写门店库存")
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
        // loss = 期初 + 入库 − 销售 − 赠送 + 退货 − 退回 − 期末 = 5 + 0 − 0 − 0 + 0 − 0 − 2.5
        assertThat(row.getLossQty()).isEqualByComparingTo("2.500");
        ArgumentCaptor<StoreInventory> invCap = ArgumentCaptor.forClass(StoreInventory.class);
        verify(storeInventoryMapper, times(1)).insert(invCap.capture());
        assertThat(invCap.getValue().getStockQty()).isEqualByComparingTo("2.500");
    }

    /** 字典业务码对得上的猪肉原材料产品（按重量盘点，未配上级原材料）。 */
    private ProductInfo porkProduct() {
        ProductInfo p = new ProductInfo();
        p.setId(PORK_PRODUCT_ID);
        p.setProductId(PORK_PRODUCT_CODE);
        p.setProductName("五花肉");
        p.setProductUnit("kg");
        p.setBelongType("pork");
        p.setIsMaterialSold(0);
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
