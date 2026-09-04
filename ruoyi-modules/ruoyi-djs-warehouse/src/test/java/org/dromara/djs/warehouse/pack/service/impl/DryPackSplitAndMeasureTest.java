package org.dromara.djs.warehouse.pack.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.djs.common.encoder.BizCodeType;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.location.domain.LocationInfo;
import org.dromara.djs.warehouse.location.mapper.LocationInfoMapper;
import org.dromara.djs.warehouse.pack.domain.ProductProduction;
import org.dromara.djs.warehouse.pack.domain.bo.DryPackBo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.dromara.djs.warehouse.product.mapper.ProductInhouseMapper;
import org.dromara.djs.warehouse.stock.domain.LocationStock;
import org.dromara.djs.warehouse.stock.mapper.LocationStockMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「其他产品打包」两条甲方口径的单测（V6 row45 + row47，2026-08-06）。
 *
 * <ul>
 *   <li><b>row45</b>：「当产品的原材料单位是KG时，其生产产品有计量规则时，在打包时判断逻辑和果蔬产品一致，
 *       低于计量不能打包，高于3%时进行提醒」→ {@code isOtherPackKgMeasureMode} 分支。</li>
 *   <li><b>row47</b>：「输入了2，代表打包了2份，在生产的时候，应该生成两条记录，每条记录的原材料消耗是计量规则……
 *       应该是每一份都是单独一条记录，原材料消耗数据是固定的」→ {@code resolveDryProductionSplitCount} 拆行。</li>
 * </ul>
 *
 * <p>拆行的三条不变式在此钉死：产出条数 = 打包量 / 每条消耗固定 / <b>总量（原材料扣减 + 需求扣减）一分不多</b>。</p>
 *
 * @author djs
 * @since V6-R45-R47
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("其他产品打包：计量规则校验（row45）+ 打包量拆产出记录（row47）")
class DryPackSplitAndMeasureTest {

    @Mock private ProductProductionMapper productionMapper;
    @Mock private ProductInhouseMapper inhouseMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private LocationInfoMapper locationInfoMapper;
    @Mock private LocationStockMapper locationStockMapper;
    @Mock private StockFlowMapper stockFlowMapper;
    @Mock private org.dromara.djs.common.store.mapper.StoreMapper storeMapper;
    @Mock private org.dromara.djs.plant.plot.mapper.PlotInfoMapper plotInfoMapper;
    @Mock private DemandManageMapper demandManageMapper;
    @Mock private org.dromara.djs.warehouse.cross.mapper.BarInfoMapper barInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private org.dromara.djs.warehouse.trace.service.ITraceService traceService;
    @Mock private org.dromara.djs.warehouse.check.service.IStockCheckService stockCheckService;

    private ProductProductionServiceImpl service;

    private static final Long INHOUSE_ID = 70001L;
    private static final Long MATERIAL_PRODUCT_ID = 60001L;
    private static final Long PRODUCT_ID = 60010L;
    private static final Long LOCATION_ID = 90001L;
    private static final Long STORE_ID = 7L;
    private static final Long DEMAND_ID = 50001L;

    /** 自增产出记录 id，验证「拆几条」时按 insert 次序落号。 */
    private AtomicInteger insertSeq;

    @BeforeAll
    static void initMpEntityCache() {
        // 无 Spring 上下文时 TableInfoHelper 解析不到 lambda 列名，预热本用例可能触达的实体
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, LocationStock.class);
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, ProductInhouse.class);
    }

    @BeforeEach
    void setup() {
        service = new ProductProductionServiceImpl(
            productionMapper, inhouseMapper, productInfoMapper,
            locationInfoMapper, locationStockMapper, stockFlowMapper, storeMapper, plotInfoMapper,
            demandManageMapper, barInfoMapper, bizCodeGenerator, traceService, stockCheckService);

        insertSeq = new AtomicInteger(0);
        // produce_no 每条各取一次号：stub 按调用次序发不同号，验证 N 条不共用一个号（表上有 UNIQUE）
        AtomicInteger codeSeq = new AtomicInteger(0);
        when(bizCodeGenerator.generate(eq(BizCodeType.PRODUCE_NO), anyMap()))
            .thenAnswer(inv -> String.format("P26080600%02d", codeSeq.incrementAndGet()));
        when(productionMapper.insert(any(ProductProduction.class))).thenAnswer(inv -> {
            ProductProduction p = inv.getArgument(0);
            p.setId(80000L + insertSeq.incrementAndGet());
            return 1;
        });
        when(inhouseMapper.deductWeightById(anyLong(), any())).thenReturn(1);
        when(inhouseMapper.deleteById(any())).thenReturn(1);
        when(locationInfoMapper.selectById(LOCATION_ID)).thenReturn(location());
    }

    // ============================================================
    // fixtures
    // ============================================================

    private static LocationInfo location() {
        LocationInfo l = new LocationInfo();
        l.setId(LOCATION_ID);
        l.setLocationName("干货库");
        return l;
    }

    /** 领用进来的原料 inhouse（其他业态、无耳号 → 不走肉品来源池）。 */
    private ProductInhouse source(String materialUnit, String remainQty) {
        ProductInhouse i = new ProductInhouse();
        i.setId(INHOUSE_ID);
        i.setProductId(MATERIAL_PRODUCT_ID);
        i.setProductName("原料");
        i.setProductUnit(materialUnit);
        i.setProductWeight(new BigDecimal(remainQty));
        i.setSource("warehouse");
        return i;
    }

    /** 其他产品打包目标成品（egg/dry_good/other 之一）。 */
    private ProductInfo product(String belongType, String productUnit, String materialNum) {
        ProductInfo p = new ProductInfo();
        p.setId(PRODUCT_ID);
        p.setProductId("PROD-OTHER-01");
        p.setProductName("其他产品");
        p.setProductType(1);
        p.setProductUnit(productUnit);
        p.setBelongType(belongType);
        p.setIsDelivery(1);
        if (materialNum != null) {
            p.setMaterialNum(new BigDecimal(materialNum));
        }
        return p;
    }

    private DryPackBo bo(String productWeight, String packQuantity, String productUnit) {
        DryPackBo b = new DryPackBo();
        b.setSourceInhouseId(INHOUSE_ID);
        b.setProductId(PRODUCT_ID);
        b.setProductWeight(new BigDecimal(productWeight));
        if (packQuantity != null) {
            b.setPackQuantity(new BigDecimal(packQuantity));
        }
        b.setProductUnit(productUnit);
        b.setLocationId(LOCATION_ID);
        b.setStoreId(STORE_ID);
        return b;
    }

    /** 门店有一条剩余 {@code remain} 份的未完成需求（用于验证「整批只扣一次」）。 */
    private void stubDemand(String demandQty, String shipped) {
        DemandManage d = new DemandManage();
        d.setId(DEMAND_ID);
        d.setDemandQuantity(new BigDecimal(demandQty));
        d.setShippedCount(new BigDecimal(shipped));
        when(demandManageMapper.selectUncompletedDemands(PRODUCT_ID, STORE_ID)).thenReturn(List.of(d));
        when(demandManageMapper.incrementShipped(anyLong(), anyString(), any())).thenReturn(1);
    }

    private List<ProductProduction> capturedProductions(int expectedCount) {
        ArgumentCaptor<ProductProduction> cap = ArgumentCaptor.forClass(ProductProduction.class);
        verify(productionMapper, times(expectedCount)).insert(cap.capture());
        return cap.getAllValues();
    }

    // ============================================================
    // row45：原材料单位 = KG + 配了计量规则 → 与果蔬同口径校验
    // ============================================================

    @Nested
    @DisplayName("row45 计量规则校验")
    class MeasureRule {

        @Test
        @DisplayName("原料 kg + 规则 0.500kg + 实称 0.400 → 硬拒「不能少于规则重量」，一条产出记录都不写")
        void belowRuleRejected() {
            when(inhouseMapper.selectById(INHOUSE_ID)).thenReturn(source("kg", "50.000"));
            when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product("dry_good", "袋", "0.500"));

            assertThatThrownBy(() -> service.submitDryPack(bo("0.400", null, "kg")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能少于规则重量")
                .hasMessageNotContaining("超出3%");
            verify(productionMapper, never()).insert(any(ProductProduction.class));
        }

        @Test
        @DisplayName("原料 kg + 实称超上界（0.560 > 0.515）未确认 → 抛带「超出3%」标识（前端据此弹二次确认）")
        void overToleranceNeedsConfirm() {
            when(inhouseMapper.selectById(INHOUSE_ID)).thenReturn(source("kg", "50.000"));
            when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product("egg", "盒", "0.500"));

            assertThatThrownBy(() -> service.submitDryPack(bo("0.560", null, "kg")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超出3%");
            verify(productionMapper, never()).insert(any(ProductProduction.class));
        }

        @Test
        @DisplayName("超上界但操作员已确认（allowOverMeasure=true）→ 放行落 1 条")
        void overToleranceConfirmedPasses() {
            when(inhouseMapper.selectById(INHOUSE_ID)).thenReturn(source("kg", "50.000"));
            when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product("egg", "盒", "0.500"));
            stubDemand("10", "0");

            DryPackBo b = bo("0.560", null, "kg");
            b.setAllowOverMeasure(true);

            assertThatCode(() -> service.submitDryPack(b)).doesNotThrowAnyException();
            verify(productionMapper, times(1)).insert(any(ProductProduction.class));
        }

        @Test
        @DisplayName("实称落在 [规则, 规则×1.03]（0.510）→ 直接通过")
        void withinTolerancePasses() {
            when(inhouseMapper.selectById(INHOUSE_ID)).thenReturn(source("公斤", "50.000"));
            when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product("other", "袋", "0.500"));
            stubDemand("10", "0");

            assertThatCode(() -> service.submitDryPack(bo("0.510", null, "公斤")))
                .doesNotThrowAnyException();
            verify(productionMapper, times(1)).insert(any(ProductProduction.class));
        }

        @Test
        @DisplayName("原料单位是「克」（重量制但非 KG）→ 不校验：录的是克、规则是 kg，拿克比 kg 会把每次打包都判成偏低")
        void gramMaterialNotValidated() {
            when(inhouseMapper.selectById(INHOUSE_ID)).thenReturn(source("克", "5000"));
            when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product("dry_good", "袋", "0.500"));
            stubDemand("10", "0");

            // 350 克 = 0.35kg，若误用「重量制」宽口径判据就会被 0.500kg 下限硬拒
            assertThatCode(() -> service.submitDryPack(bo("350", null, "克")))
                .doesNotThrowAnyException();
            verify(productionMapper, times(1)).insert(any(ProductProduction.class));
        }

        @Test
        @DisplayName("原料 kg 但未配计量规则（material_num 空）→ 不校验，任意实称放行")
        void noMeasureRuleNotValidated() {
            when(inhouseMapper.selectById(INHOUSE_ID)).thenReturn(source("kg", "50.000"));
            when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product("dry_good", "袋", null));
            stubDemand("10", "0");

            assertThatCode(() -> service.submitDryPack(bo("0.001", null, "kg")))
                .doesNotThrowAnyException();
            verify(productionMapper, times(1)).insert(any(ProductProduction.class));
        }

        @Test
        @DisplayName("打包量模式（回传 packQuantity）即便原料标成 kg 也不套重量规则 —— row45/row47 两支互斥")
        void amountModeExcludedFromMeasureRule() {
            when(inhouseMapper.selectById(INHOUSE_ID)).thenReturn(source("kg", "50.000"));
            when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product("dry_good", "份", "0.500"));
            stubDemand("10", "0");

            // 打包 2 份 × 0.5 = 1.0；若误套重量规则会拿 1.0 与 0.5×1.03 比 → 被判「超出3%」拦下
            assertThatCode(() -> service.submitDryPack(bo("1.000", "2", "kg")))
                .doesNotThrowAnyException();
        }
    }

    // ============================================================
    // row47：打包 N 份 → N 条产出记录，每条消耗固定 = 计量规则
    // ============================================================

    @Nested
    @DisplayName("row47 打包量拆产出记录")
    class SplitByPackQuantity {

        /** 鸡蛋：原料按枚计量、每份 5 枚。 */
        private void stubEgg() {
            when(inhouseMapper.selectById(INHOUSE_ID)).thenReturn(source("枚", "500"));
            when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product("egg", "份", "5"));
        }

        @Test
        @DisplayName("N=1：落 1 条，消耗 = 本次总量（与拆行前完全一致）")
        void singleCopy() {
            stubEgg();
            stubDemand("10", "0");

            Long id = service.submitDryPack(bo("5", "1", "枚"));

            List<ProductProduction> rows = capturedProductions(1);
            assertThat(id).isEqualTo(rows.get(0).getId());
            assertThat(rows.get(0).getProductWeight()).isEqualByComparingTo("5");
            assertThat(rows.get(0).getMaterialConsume()).isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("N=3：落 3 条，每条消耗固定 5 枚（= 计量规则），不是 1 条消耗 15 枚")
        void threeCopiesThreeRows() {
            stubEgg();
            stubDemand("10", "0");

            // 前端提交：打包量 3 份，原材料总消耗 3 × 5 = 15 枚
            service.submitDryPack(bo("15", "3", "枚"));

            List<ProductProduction> rows = capturedProductions(3);
            assertThat(rows).extracting(ProductProduction::getProductWeight)
                .allSatisfy(w -> assertThat(w).isEqualByComparingTo("5"));
            assertThat(rows).extracting(ProductProduction::getMaterialConsume)
                .allSatisfy(w -> assertThat(w).isEqualByComparingTo("5"));
            // 原材料溯源列指向来源 inhouse 的原料 productId
            assertThat(rows).extracting(ProductProduction::getMaterialId)
                .containsOnly(MATERIAL_PRODUCT_ID);
        }

        @Test
        @DisplayName("N=3：3 条各自一个 produce_no（表上 UNIQUE(tenant_id, produce_no, del_unique)，共用必撞）")
        void eachRowGetsOwnProduceNo() {
            stubEgg();
            stubDemand("10", "0");

            service.submitDryPack(bo("15", "3", "枚"));

            List<ProductProduction> rows = capturedProductions(3);
            assertThat(rows).extracting(ProductProduction::getProduceNo).doesNotHaveDuplicates();
            verify(bizCodeGenerator, times(3)).generate(eq(BizCodeType.PRODUCE_NO), anyMap());
        }

        @Test
        @DisplayName("N=3：原材料只按总量扣一次（15 枚），不是每条各扣 15")
        void materialDeductedOnceByTotal() {
            stubEgg();
            stubDemand("10", "0");

            service.submitDryPack(bo("15", "3", "枚"));

            verify(inhouseMapper, times(1)).deductWeightById(eq(INHOUSE_ID), eq(new BigDecimal("15")));
            verify(inhouseMapper, never()).deleteById(any());
        }

        @Test
        @DisplayName("N=3：门店需求整批只扣 3 份（不是 3 条各扣 3 = 9）")
        void demandDeductedOnceByPackQuantity() {
            stubEgg();
            stubDemand("10", "0");

            service.submitDryPack(bo("15", "3", "枚"));

            verify(demandManageMapper, times(1))
                .incrementShipped(eq(DEMAND_ID), eq("1001"), eq(new BigDecimal("3")));
        }

        @Test
        @DisplayName("Σ 每条消耗 ≡ 提交总量：除不尽时尾差落最后一条，总扣减量一分不差")
        void remainderGoesToLastRow() {
            when(inhouseMapper.selectById(INHOUSE_ID)).thenReturn(source("枚", "500"));
            when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product("egg", "份", null));
            stubDemand("10", "0");

            // 10 ÷ 3 除不尽：前两条 3.333，最后一条 10 − 6.666 = 3.334
            service.submitDryPack(bo("10", "3", "枚"));

            List<ProductProduction> rows = capturedProductions(3);
            BigDecimal sum = rows.stream()
                .map(ProductProduction::getProductWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).isEqualByComparingTo("10");
            assertThat(rows.get(0).getProductWeight()).isEqualByComparingTo("3.333");
            assertThat(rows.get(2).getProductWeight()).isEqualByComparingTo("3.334");
        }

        @Test
        @DisplayName("KG 成品（重量模式）即便带了 packQuantity 也恒 1 条 —— 录的是重量不是份数")
        void kgProductNeverSplits() {
            when(inhouseMapper.selectById(INHOUSE_ID)).thenReturn(source("kg", "50.000"));
            when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product("dry_good", "kg", null));
            DemandManage d = new DemandManage();
            d.setId(DEMAND_ID);
            d.setDemandQuantity(new BigDecimal("2.000"));
            d.setShippedCount(BigDecimal.ZERO);
            when(demandManageMapper.selectUncompletedDemands(PRODUCT_ID, STORE_ID))
                .thenReturn(java.util.List.of(d));
            when(demandManageMapper.incrementShipped(anyLong(), anyString(), any())).thenReturn(1);

            service.submitDryPack(bo("3.000", "3", "kg"));

            verify(productionMapper, times(1)).insert(any(ProductProduction.class));
        }

        @Test
        @DisplayName("未回传打包量（mp / 肉品页 / 老客户端）→ 恒 1 条，行为与拆行前一致")
        void noPackQuantityNeverSplits() {
            stubEgg();
            stubDemand("10", "0");

            service.submitDryPack(bo("15", null, "枚"));

            List<ProductProduction> rows = capturedProductions(1);
            assertThat(rows.get(0).getProductWeight()).isEqualByComparingTo("15");
            // 回落原口径「一次打包扣 1 份」
            verify(demandManageMapper).incrementShipped(eq(DEMAND_ID), eq("1001"), eq(BigDecimal.ONE));
        }

        @Test
        @DisplayName("打包量不是整数（2.5 份）→ 直接拒，不写任何记录（半份产品不存在）")
        void fractionalPackQuantityRejected() {
            stubEgg();

            assertThatThrownBy(() -> service.submitDryPack(bo("12.5", "2.5", "枚")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("打包量必须是整数份");
            verify(productionMapper, never()).insert(any(ProductProduction.class));
        }

        @Test
        @DisplayName("打包量超单次上限（501 份）→ 直接拒，防手改请求把一个事务撑爆")
        void oversizedPackQuantityRejected() {
            // 领用余量给足，确保拦下它的是拆行上限而不是「库存不足」
            when(inhouseMapper.selectById(INHOUSE_ID)).thenReturn(source("枚", "99999"));
            when(productInfoMapper.selectById(PRODUCT_ID)).thenReturn(product("egg", "份", "5"));

            assertThatThrownBy(() -> service.submitDryPack(bo("2505", "501", "枚")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("单次打包量过大");
            verify(productionMapper, never()).insert(any(ProductProduction.class));
        }
    }
}
