package org.dromara.djs.warehouse.selfcheck.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.common.supplier.domain.Supplier;
import org.dromara.djs.common.supplier.mapper.SupplierMapper;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.cross.domain.BarInfo;
import org.dromara.djs.warehouse.cross.mapper.BarInfoMapper;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.selfcheck.domain.bo.ProductInboundBo;
import org.dromara.djs.warehouse.selfcheck.domain.bo.ProductOutboundBo;
import org.dromara.djs.warehouse.selfcheck.domain.bo.StockCheckEntryBo;
import org.dromara.djs.warehouse.selfcheck.mapper.StockSelfMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StockSelfServiceImpl} 单测（F0-8 验收门 · TEST-GAP-3）。
 *
 * <p>mp 仓库自助出入库写链（5 处写点）此前 0 测试。覆盖流水 ↔ 货架双账簿对账：</p>
 * <ol>
 *   <li>inbound happy：流水 IN/purchase_in/+q + addByProductLocation 同量加账（不建新行）</li>
 *   <li>inbound 首次建账：addByProductLocation 返 0 → 兜底 INSERT 新库存行 stock=q</li>
 *   <li>outbound happy：流水 OT/change_num=-q + deductByProductLocation 同量扣账（对账一致）</li>
 *   <li>outbound 库存不足：deduct 返 0 → 抛异常（@Transactional 连流水回滚，防单边分叉）</li>
 *   <li>checkSubmit 计损：计损量 5 为权威损失量 → 流水 check_out/-5 + 货架校准 sys-5 + 统一损耗台账双写</li>
 * </ol>
 *
 * @author djs
 * @since F0-8
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StockSelfServiceImpl 单元测试（F0-8 · TEST-GAP-3）")
class StockSelfServiceImplTest {

    @Mock private StockSelfMapper stockSelfMapper;
    @Mock private LocationStockMapper locationStockMapper;
    @Mock private StockFlowMapper stockFlowMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private BarInfoMapper barInfoMapper;
    @Mock private SupplierMapper supplierMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private IStockCheckService stockCheckService;
    @Mock private ILossFlowService lossFlowService;
    @Mock private ImageUrlResolver imageUrlResolver;

    private StockSelfServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    private static final Long USER_ID = 9001L;
    private static final Long PRODUCT_ID = 8001L;
    private static final Long LOCATION_ID = 7001L;

    /**
     * MyBatis-Plus 单测 entity cache 预热（skill coder-mp-entity-cache-test）：
     * requireProduct / currentStock / resolveSupplierId / listWhiteBarStocks 用 lambda wrapper。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, LocationStock.class);
        TableInfoHelper.initTableInfo(assistant, Supplier.class);
        TableInfoHelper.initTableInfo(assistant, BarInfo.class);
    }

    @BeforeEach
    void setup() {
        service = new StockSelfServiceImpl(
            stockSelfMapper, locationStockMapper, stockFlowMapper, productInfoMapper,
            barInfoMapper, supplierMapper, bizCodeGenerator, stockCheckService, lossFlowService,
            imageUrlResolver);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);

        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductName("土鸡蛋");
        product.setProductUnit("kg");
        when(productInfoMapper.selectOne(any())).thenReturn(product);

        when(bizCodeGenerator.generate(any(), any())).thenReturn("FAKE_FLOW_NO");
        when(stockFlowMapper.insert(any(StockFlow.class))).thenAnswer(inv -> {
            StockFlow f = inv.getArgument(0);
            f.setId(50002L);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private ProductInboundBo inboundBo(BigDecimal qty) {
        ProductInboundBo bo = new ProductInboundBo();
        bo.setLocationId(String.valueOf(LOCATION_ID));
        bo.setProductId(String.valueOf(PRODUCT_ID));
        bo.setQuantity(qty);
        bo.setInoutType("purchase");
        return bo;
    }

    private ProductOutboundBo outboundBo(BigDecimal qty) {
        ProductOutboundBo bo = new ProductOutboundBo();
        bo.setLocationId(String.valueOf(LOCATION_ID));
        bo.setProductId(String.valueOf(PRODUCT_ID));
        bo.setQuantity(qty);
        // flow_type 由 inoutType 映射（mapOutboundFlowType：dept_pick → dept_pick_out）
        bo.setInoutType("dept_pick");
        bo.setStockOutDest("dept_pick");
        return bo;
    }

    @Test
    @DisplayName("inbound happy：流水 IN/purchase_in/change_num=+50/warehouse_id=库位 + addByProductLocation 同量加账 → 不建新行")
    void testInbound_Happy() {
        when(locationStockMapper.addByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        Long flowId = service.inbound(inboundBo(new BigDecimal("50")));
        assertThat(flowId).isEqualTo(50002L);

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getInoutType()).isEqualTo("IN");
        assertThat(f.getFlowType()).isEqualTo("purchase_in");
        assertThat(f.getChangeNum()).isEqualByComparingTo("50");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("50");
        assertThat(f.getWarehouseId()).isEqualTo(LOCATION_ID);
        assertThat(f.getOperatorId()).isEqualTo(USER_ID);
        // 双账簿对账：货架加量 == 流水量
        verify(locationStockMapper, times(1))
            .addByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), eq(new BigDecimal("50")), eq(USER_ID));
        verify(locationStockMapper, never()).insert(any(LocationStock.class));
    }

    @Test
    @DisplayName("inbound 首次建账：addByProductLocation 返 0 → 兜底 INSERT 新库存行 stock=q")
    void testInbound_FirstTime_InsertRow() {
        when(locationStockMapper.addByProductLocation(any(), any(), any(), any())).thenReturn(0);

        service.inbound(inboundBo(new BigDecimal("20")));

        ArgumentCaptor<LocationStock> cap = ArgumentCaptor.forClass(LocationStock.class);
        verify(locationStockMapper, times(1)).insert(cap.capture());
        LocationStock row = cap.getValue();
        assertThat(row.getLocationId()).isEqualTo(LOCATION_ID);
        assertThat(row.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(row.getProductStock()).isEqualByComparingTo("20");
        assertThat(row.getIsEnd()).isEqualTo(0);
        verify(stockFlowMapper, times(1)).insert(any(StockFlow.class));
    }

    @Test
    @DisplayName("outbound happy：流水 OT/dept_pick_out/change_num=-20 + deductByProductLocation 同量扣账（流水↔货架对账一致）")
    void testOutbound_Happy_FlowMatchesDeduct() {
        when(locationStockMapper.deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        Long flowId = service.outbound(outboundBo(new BigDecimal("20")));
        assertThat(flowId).isEqualTo(50002L);

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getInoutType()).isEqualTo("OT");
        // inoutType=dept_pick → flow_type 拆 dept_pick_out（FIX-WMS-FLOWDICT-001）
        assertThat(f.getFlowType()).isEqualTo("dept_pick_out");
        assertThat(f.getStockOutDest()).isEqualTo("dept_pick");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-20");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("20");
        // 对账核心：货架扣减量 == 流水 changeQuantity == |changeNum|
        verify(locationStockMapper, times(1))
            .deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), eq(new BigDecimal("20")), eq(USER_ID));
    }

    @Test
    @DisplayName("outbound 库存不足：deductByProductLocation 返 0 → 抛'库存不足'（@Transactional 连流水回滚，防流水与货架单边分叉）")
    void testOutbound_Insufficient_Throws() {
        when(locationStockMapper.deductByProductLocation(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.outbound(outboundBo(new BigDecimal("999"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("库存不足");

        // 流水 INSERT 已发生但异常抛出 → @Transactional 回滚（单测验证中断链路）
        verify(stockFlowMapper, times(1)).insert(any(StockFlow.class));
    }

    @Test
    @DisplayName("checkSubmit 计损：sys=20（组 SUM）/ 计损量 5 → 流水 check_out/OT/-5/dest=check_loss + 货架校准 15 + loss_flow 双写 5")
    void testCheckSubmit_Loss() {
        // 系统现量 = 组 SUM（F0-1：多行篮子组不再 LIMIT 1 任取一行）；本组无篮子行 → 篮子合计默认 0
        when(locationStockMapper.sumStockByProductLocation(LOCATION_ID, PRODUCT_ID))
            .thenReturn(new BigDecimal("20"));
        when(locationStockMapper.setStockAfterCheck(eq(LOCATION_ID), eq(PRODUCT_ID), any(), any(), eq(USER_ID)))
            .thenReturn(1);

        StockCheckEntryBo bo = new StockCheckEntryBo();
        bo.setLocationId(String.valueOf(LOCATION_ID));
        bo.setProductId(String.valueOf(PRODUCT_ID));
        bo.setCheckStock(new BigDecimal("20"));
        bo.setCheckResult("loss");
        bo.setDiffQuantity(new BigDecimal("5"));
        bo.setDiffReason("破损计损");

        Long flowId = service.checkSubmit(bo);
        assertThat(flowId).isEqualTo(50002L);

        // 1. 计损量 5 为权威损失量（R80/R82：不再用 checkStock-sys 的 0 差异误记正常）
        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getFlowType()).isEqualTo("check_out");
        assertThat(f.getStockOutDest()).isEqualTo("check_loss");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-5");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("5");
        // 2. 货架校准到 sys - loss = 15，check_result=计损(3)
        verify(locationStockMapper, times(1))
            .setStockAfterCheck(eq(LOCATION_ID), eq(PRODUCT_ID), eq(new BigDecimal("15")), eq(3), eq(USER_ID));
        // 3. 统一损耗台账双写：损耗量 = 5，关联本盘点流水
        verify(lossFlowService, times(1)).record(
            eq("check_loss"), eq(PRODUCT_ID), eq(new BigDecimal("5")), eq(LOCATION_ID),
            eq(USER_ID), eq("self_check"), isNull(), eq(50002L));
    }

    @Test
    @DisplayName("checkSubmit（F0-1）篮子组：组 SUM=30 全在篮子行 / 实盘 28 → 非篮子行按 0 校准 + 不建重复非篮行，差异只留流水")
    void testCheckSubmit_BasketOnlyGroup_NoDuplicateRow() {
        // 组内 30 全在耳号/白条篮子行上（如猪肉鲜品库 11 耳号篮）：系统量 = 组 SUM = 30
        when(locationStockMapper.sumStockByProductLocation(LOCATION_ID, PRODUCT_ID))
            .thenReturn(new BigDecimal("30"));
        when(locationStockMapper.sumBasketStockByProductLocation(LOCATION_ID, PRODUCT_ID))
            .thenReturn(new BigDecimal("30"));
        // 非篮子行 UPDATE 无命中
        when(locationStockMapper.setStockAfterCheck(eq(LOCATION_ID), eq(PRODUCT_ID), any(), any(), eq(USER_ID)))
            .thenReturn(0);

        StockCheckEntryBo bo = new StockCheckEntryBo();
        bo.setLocationId(String.valueOf(LOCATION_ID));
        bo.setProductId(String.valueOf(PRODUCT_ID));
        bo.setCheckStock(new BigDecimal("28"));
        bo.setCheckResult("normal");

        service.checkSubmit(bo);

        // 非篮目标量 = 28 − 30 floor 到 0（篮子行的账随各自业务链走，不在此校准）
        verify(locationStockMapper, times(1))
            .setStockAfterCheck(eq(LOCATION_ID), eq(PRODUCT_ID), eq(BigDecimal.ZERO), eq(1), eq(USER_ID));
        // 不新增实盘量非篮行（否则组合计 = 30 篮子 + 28 新行 ≈ 2 倍 = STOCK-D2-01 错账根因）
        verify(locationStockMapper, never()).insert(any(LocationStock.class));
        // 差异流水照写（-2 留痕）
        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        assertThat(cap.getValue().getChangeNum()).isEqualByComparingTo("-2");
    }

}
