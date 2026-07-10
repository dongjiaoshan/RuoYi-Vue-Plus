package org.dromara.djs.warehouse.stock.api;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.common.image.service.ImageUrlResolver;
import org.dromara.djs.common.medicine.api.MedicineProductDto;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.loss.service.ILossFlowService;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MedicineStockProviderImpl} 单测（F0-8 验收门 · TEST-GAP-6，直接验证 F0-3 补流水修复）。
 *
 * <p>药品 provider（breed↔warehouse facade 的仓库侧真实现）此前从未被执行验证。覆盖全链：</p>
 * <ol>
 *   <li>deduct happy：解析默认库位 → 原子扣减 + 流水 OT/dept_pick_out/change_num=-q（F0-3 新加写入）</li>
 *   <li>deductLoss happy：流水 OT/loss + 双写统一损耗台账（manual_loss，关联流水）</li>
 *   <li>deduct 无库位：selectDefaultLocationByProduct 返 null → 抛"药品无库存" + 零写入</li>
 *   <li>deduct 库存不足：deductByProductLocation 返 0 → 抛"药品库存不足" + 不写流水/损耗台账（扣减成功才写）</li>
 *   <li>add happy：加库存 + 流水 IN/pick_return_in/change_num=+q（领用退回）</li>
 *   <li>addPurchase happy：加库存 + 流水 IN/purchase_in（批次采购入库，与退回分流）</li>
 *   <li>add 库存行不存在：addByProductLocation 返 0 → 抛 + 不写流水</li>
 *   <li>getStocks：SUM 行 → Map&lt;productId, stock&gt; 映射正确</li>
 *   <li>listMedicineProducts：商品行 → DTO 字段映射 + resolver 回填 imageUrl</li>
 * </ol>
 *
 * @author djs
 * @since F0-8
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MedicineStockProviderImpl 单元测试（F0-8 · TEST-GAP-6）")
class MedicineStockProviderImplTest {

    @Mock private LocationStockMapper locationStockMapper;
    @Mock private ImageUrlResolver imageUrlResolver;
    @Mock private StockFlowMapper stockFlowMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private ILossFlowService lossFlowService;

    private MedicineStockProviderImpl provider;

    private static final Long PRODUCT_ID = 8801L;
    private static final Long LOCATION_ID = 7701L;
    private static final Long OPERATOR_ID = 9001L;

    @BeforeEach
    void setup() {
        provider = new MedicineStockProviderImpl(
            locationStockMapper, imageUrlResolver, stockFlowMapper, bizCodeGenerator, lossFlowService);
        when(bizCodeGenerator.generate(any(), any())).thenReturn("FAKE_FLOW_NO");
        when(stockFlowMapper.insert(any(StockFlow.class))).thenReturn(1);
    }

    @Test
    @DisplayName("deduct happy：默认库位解析 → deductByProductLocation 扣账 + 流水 OT/dept_pick_out/change_num=-3（F0-3 修复：领用/损耗必落流水）")
    void testDeduct_Happy_WritesFlow() {
        when(locationStockMapper.selectDefaultLocationByProduct(PRODUCT_ID)).thenReturn(LOCATION_ID);
        when(locationStockMapper.deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(OPERATOR_ID)))
            .thenReturn(1);

        provider.deduct(PRODUCT_ID, new BigDecimal("3"), OPERATOR_ID);

        // 双账簿：库存扣减量 == 流水量
        verify(locationStockMapper, times(1))
            .deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), eq(new BigDecimal("3")), eq(OPERATOR_ID));
        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getFlowType()).isEqualTo("dept_pick_out");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-3");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("3");
        assertThat(f.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(f.getWarehouseId()).isEqualTo(LOCATION_ID);
        assertThat(f.getOperatorId()).isEqualTo(OPERATOR_ID);
        assertThat(f.getFlowNo()).isEqualTo("FAKE_FLOW_NO");
        // 领用不是损耗：不写损耗台账
        verify(lossFlowService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("deductLoss happy：扣账 + 流水 OT/loss + 双写统一损耗台账 manual_loss（F0-3 修复：损耗总览不再对药品盲）")
    void testDeductLoss_Happy_WritesFlowAndLossFlow() {
        when(locationStockMapper.selectDefaultLocationByProduct(PRODUCT_ID)).thenReturn(LOCATION_ID);
        when(locationStockMapper.deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(OPERATOR_ID)))
            .thenReturn(1);

        provider.deductLoss(PRODUCT_ID, new BigDecimal("2.5"), OPERATOR_ID);

        // 三账：库存扣减 + 出库流水（loss）+ 损耗台账（manual_loss，关联流水）
        verify(locationStockMapper, times(1))
            .deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), eq(new BigDecimal("2.5")), eq(OPERATOR_ID));
        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getFlowType()).isEqualTo("loss");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-2.5");
        verify(lossFlowService, times(1)).record(eq("manual_loss"), eq(PRODUCT_ID),
            eq(new BigDecimal("2.5")), eq(LOCATION_ID), eq(OPERATOR_ID), eq("med"), eq((Long) null), any());
    }

    @Test
    @DisplayName("deduct 无库位：selectDefaultLocationByProduct 返 null → 抛'药品无库存' + 零写入")
    void testDeduct_NoLocation_Throws() {
        when(locationStockMapper.selectDefaultLocationByProduct(PRODUCT_ID)).thenReturn(null);

        assertThatThrownBy(() -> provider.deduct(PRODUCT_ID, BigDecimal.ONE, OPERATOR_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("药品无库存");

        verify(locationStockMapper, never()).deductByProductLocation(any(), any(), any(), any());
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
    }

    @Test
    @DisplayName("deduct 库存不足：deductByProductLocation 返 0 → 抛'药品库存不足' + 不写流水（防流水与货架单边分叉）")
    void testDeduct_Insufficient_NoFlow() {
        when(locationStockMapper.selectDefaultLocationByProduct(PRODUCT_ID)).thenReturn(LOCATION_ID);
        when(locationStockMapper.deductByProductLocation(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> provider.deduct(PRODUCT_ID, new BigDecimal("999"), OPERATOR_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("药品库存不足");
        assertThatThrownBy(() -> provider.deductLoss(PRODUCT_ID, new BigDecimal("999"), OPERATOR_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("药品库存不足");

        // 扣减成功才写流水：失败路径零流水/零损耗台账（F0-3 语义 — 不产生幽灵流水）
        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
        verify(lossFlowService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("add happy：addByProductLocation 加账 + 流水 IN/pick_return_in/change_num=+5（领用退回，不污染月采购统计）")
    void testAdd_Happy_WritesFlow() {
        when(locationStockMapper.selectDefaultLocationByProduct(PRODUCT_ID)).thenReturn(LOCATION_ID);
        when(locationStockMapper.addByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(OPERATOR_ID)))
            .thenReturn(1);

        provider.add(PRODUCT_ID, new BigDecimal("5"), OPERATOR_ID);

        verify(locationStockMapper, times(1))
            .addByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), eq(new BigDecimal("5")), eq(OPERATOR_ID));
        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getInoutType()).isEqualTo("IN");
        assertThat(f.getFlowType()).isEqualTo("pick_return_in");
        assertThat(f.getChangeNum()).isEqualByComparingTo("5");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("5");
        assertThat(f.getWarehouseId()).isEqualTo(LOCATION_ID);
    }

    @Test
    @DisplayName("addPurchase happy：加账 + 流水 IN/purchase_in（批次采购入库，计入月采购统计）")
    void testAddPurchase_Happy_WritesFlow() {
        when(locationStockMapper.selectDefaultLocationByProduct(PRODUCT_ID)).thenReturn(LOCATION_ID);
        when(locationStockMapper.addByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(OPERATOR_ID)))
            .thenReturn(1);

        provider.addPurchase(PRODUCT_ID, new BigDecimal("120"), OPERATOR_ID);

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getInoutType()).isEqualTo("IN");
        assertThat(f.getFlowType()).isEqualTo("purchase_in");
        assertThat(f.getChangeNum()).isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("add 库存行不存在：addByProductLocation 返 0 → 抛'药品退回失败' + 不写流水")
    void testAdd_NoStockRow_NoFlow() {
        when(locationStockMapper.selectDefaultLocationByProduct(PRODUCT_ID)).thenReturn(LOCATION_ID);
        when(locationStockMapper.addByProductLocation(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> provider.add(PRODUCT_ID, BigDecimal.ONE, OPERATOR_ID))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("药品退回失败");

        verify(stockFlowMapper, never()).insert(any(StockFlow.class));
    }

    @Test
    @DisplayName("getStocks：SUM 行（BigInteger/BigDecimal JDBC 混型）→ Map<productId, stock> 映射正确")
    void testGetStocks_Mapping() {
        Map<String, Object> row1 = new HashMap<>();
        row1.put("product_id", 8801L);
        row1.put("stock", new BigDecimal("12.500"));
        Map<String, Object> row2 = new HashMap<>();
        row2.put("product_id", java.math.BigInteger.valueOf(8802L));
        row2.put("stock", 7);
        when(locationStockMapper.selectProductStocks(any())).thenReturn(List.of(row1, row2));

        Map<Long, BigDecimal> stocks = provider.getStocks(List.of(8801L, 8802L));

        assertThat(stocks).hasSize(2);
        assertThat(stocks.get(8801L)).isEqualByComparingTo("12.5");
        assertThat(stocks.get(8802L)).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("listMedicineProducts：商品行 → DTO 字段映射（id/name/unit/spec/stock）+ resolver 回填 imageUrl")
    void testListMedicineProducts_DtoMapping() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 8801L);
        row.put("name", "土霉素");
        row.put("unit", "瓶");
        row.put("spec", "100ml");
        row.put("stock", new BigDecimal("20"));
        row.put("imageId", "oss-123");
        when(locationStockMapper.selectMedicineProducts("土")).thenReturn(List.of(row));
        when(imageUrlResolver.resolveList(any())).thenReturn(List.of("https://oss/med-123.png"));

        List<MedicineProductDto> list = provider.listMedicineProducts("土");

        assertThat(list).hasSize(1);
        MedicineProductDto dto = list.get(0);
        assertThat(dto.getId()).isEqualTo(8801L);
        assertThat(dto.getName()).isEqualTo("土霉素");
        assertThat(dto.getUnit()).isEqualTo("瓶");
        assertThat(dto.getSpec()).isEqualTo("100ml");
        assertThat(dto.getStock()).isEqualByComparingTo("20");
        assertThat(dto.getImageUrl()).isEqualTo("https://oss/med-123.png");
    }

}
