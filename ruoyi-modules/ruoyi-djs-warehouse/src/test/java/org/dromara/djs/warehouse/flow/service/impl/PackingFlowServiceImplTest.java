package org.dromara.djs.warehouse.flow.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.common.encoder.IBizCodeGenerator;
import org.dromara.djs.warehouse.check.service.IStockCheckService;
import org.dromara.djs.warehouse.flow.domain.StockFlow;
import org.dromara.djs.warehouse.flow.domain.bo.PackCheckBo;
import org.dromara.djs.warehouse.flow.domain.bo.PackInBo;
import org.dromara.djs.warehouse.flow.domain.bo.PackOutBo;
import org.dromara.djs.warehouse.flow.mapper.StockFlowMapper;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PackingFlowServiceImpl} 单测（F0-8 验收门 · TEST-GAP-2）。
 *
 * <p>mp 打包/包材工序双账簿写链（stock_flow + location_stock）此前 0 测试。覆盖：</p>
 * <ol>
 *   <li>packIn happy：流水 IN/purchase_in/+q + addByProductLocation 命中既有行（不建新行）</li>
 *   <li>packIn 首次入库：addByProductLocation 返 0 → 兜底 INSERT 新库存行（stock=q，双账簿仍一致）</li>
 *   <li>packOut 正常量：流水 OT/prod_pick_out/dest=prod_pick/change_num=-q + 扣减量与流水量对账一致</li>
 *   <li>packOut 超量：deductByProductLocation 返 0 → 抛"库存不足"（@Transactional 回滚流水，货架不动）</li>
 *   <li>packCheck 盘亏：系统量 10 / 实盘 7 → 差异流水 check_out/OT/-3 + setStockAfterCheck 校准到 7</li>
 * </ol>
 *
 * @author djs
 * @since F0-8
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PackingFlowServiceImpl 单元测试（F0-8 · TEST-GAP-2）")
class PackingFlowServiceImplTest {

    @Mock private StockFlowMapper stockFlowMapper;
    @Mock private LocationStockMapper locationStockMapper;
    @Mock private ProductInfoMapper productInfoMapper;
    @Mock private IBizCodeGenerator bizCodeGenerator;
    @Mock private IStockCheckService stockCheckService;

    private PackingFlowServiceImpl service;
    private MockedStatic<LoginHelper> loginHelperMock;

    private static final Long USER_ID = 9001L;
    private static final Long PRODUCT_ID = 8001L;
    private static final Long LOCATION_ID = 7001L;

    /**
     * MyBatis-Plus 单测 entity cache 预热（skill coder-mp-entity-cache-test）：
     * requireProduct 用 LambdaQueryWrapper&lt;ProductInfo&gt;、currentStock 用 LambdaQueryWrapper&lt;LocationStock&gt;。
     */
    @BeforeAll
    static void initMpEntityCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, ProductInfo.class);
        TableInfoHelper.initTableInfo(assistant, LocationStock.class);
    }

    @BeforeEach
    void setup() {
        service = new PackingFlowServiceImpl(
            stockFlowMapper, locationStockMapper, productInfoMapper, bizCodeGenerator, stockCheckService);
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(USER_ID);

        ProductInfo product = new ProductInfo();
        product.setId(PRODUCT_ID);
        product.setProductName("包装纸箱");
        product.setProductUnit("个");
        product.setBelongType("package");
        when(productInfoMapper.selectOne(any())).thenReturn(product);

        when(bizCodeGenerator.generate(any(), any())).thenReturn("FAKE_FLOW_NO");
        when(stockFlowMapper.insert(any(StockFlow.class))).thenAnswer(inv -> {
            StockFlow f = inv.getArgument(0);
            f.setId(50001L);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private PackInBo packInBo(BigDecimal qty) {
        PackInBo bo = new PackInBo();
        bo.setProductId(PRODUCT_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setQuantity(qty);
        bo.setRemark("ut-packin");
        return bo;
    }

    private PackOutBo packOutBo(BigDecimal qty) {
        PackOutBo bo = new PackOutBo();
        bo.setProductId(PRODUCT_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setQuantity(qty);
        bo.setStockOutDest("prod_pick");
        bo.setRemark("ut-packout");
        return bo;
    }

    @Test
    @DisplayName("packIn happy：流水 IN/purchase_in/change_num=+100 + addByProductLocation 命中既有行 → 不建新行")
    void testPackIn_Happy() {
        when(locationStockMapper.addByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        Long flowId = service.packIn(packInBo(new BigDecimal("100")));
        assertThat(flowId).isEqualTo(50001L);

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getInoutType()).isEqualTo("IN");
        assertThat(f.getFlowType()).isEqualTo("purchase_in");
        assertThat(f.getChangeNum()).isEqualByComparingTo("100");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("100");
        assertThat(f.getWarehouseId()).isEqualTo(LOCATION_ID);
        assertThat(f.getOperatorId()).isEqualTo(USER_ID);
        // 双账簿：流水量 == 货架加量；命中既有行不再建新行
        verify(locationStockMapper, times(1))
            .addByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), eq(new BigDecimal("100")), eq(USER_ID));
        verify(locationStockMapper, never()).insert(any(LocationStock.class));
    }

    @Test
    @DisplayName("packIn 首次入库：addByProductLocation 返 0（组内无非篮子行）→ 兜底 INSERT 新库存行 stock=q")
    void testPackIn_FirstTime_InsertRow() {
        when(locationStockMapper.addByProductLocation(any(), any(), any(), any())).thenReturn(0);

        service.packIn(packInBo(new BigDecimal("30")));

        ArgumentCaptor<LocationStock> cap = ArgumentCaptor.forClass(LocationStock.class);
        verify(locationStockMapper, times(1)).insert(cap.capture());
        LocationStock row = cap.getValue();
        assertThat(row.getLocationId()).isEqualTo(LOCATION_ID);
        assertThat(row.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(row.getProductStock()).isEqualByComparingTo("30");
        assertThat(row.getIsEnd()).isEqualTo(0);
        // 流水照写（入库建账 + 流水同批）
        verify(stockFlowMapper, times(1)).insert(any(StockFlow.class));
    }

    @Test
    @DisplayName("packOut 正常量：流水 OT/prod_pick_out/dest=prod_pick/change_num=-30 + 扣减量与流水 changeQuantity 一致")
    void testPackOut_Happy() {
        when(locationStockMapper.deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), any(BigDecimal.class), eq(USER_ID)))
            .thenReturn(1);

        Long flowId = service.packOut(packOutBo(new BigDecimal("30")));
        assertThat(flowId).isEqualTo(50001L);

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getInoutType()).isEqualTo("OT");
        // 包材出库 = 仓库生产消耗，flow_type 强制 prod_pick_out + dest 强制 prod_pick（不信 mp 传值）
        assertThat(f.getFlowType()).isEqualTo("prod_pick_out");
        assertThat(f.getStockOutDest()).isEqualTo("prod_pick");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-30");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("30");
        // 双账簿对账：货架扣减量 == 流水 changeQuantity
        verify(locationStockMapper, times(1))
            .deductByProductLocation(eq(LOCATION_ID), eq(PRODUCT_ID), eq(new BigDecimal("30")), eq(USER_ID));
    }

    @Test
    @DisplayName("packOut 超量：deductByProductLocation 返 0 → 抛'库存不足'（@Transactional 连流水一起回滚）")
    void testPackOut_OverStock_Throws() {
        when(locationStockMapper.deductByProductLocation(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.packOut(packOutBo(new BigDecimal("999"))))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("库存不足");

        // 流水 INSERT 已发生但异常抛出 → Spring @Transactional 回滚（回滚行为由集成环境保证，单测验证中断链路）
        verify(stockFlowMapper, times(1)).insert(any(StockFlow.class));
    }

    @Test
    @DisplayName("packCheck 盘亏：系统量 10（组 SUM）/ 实盘 7 → 差异流水 check_out/OT/change_num=-3 + setStockAfterCheck 校准到 7（结果=异常）")
    void testPackCheck_Deficit() {
        // 系统现量 = 组 SUM（F0-1：多行组不再 LIMIT 1 任取一行）；包材无篮子行 → 篮子合计默认 0
        when(locationStockMapper.sumStockByProductLocation(LOCATION_ID, PRODUCT_ID))
            .thenReturn(new BigDecimal("10"));
        when(locationStockMapper.setStockAfterCheck(eq(LOCATION_ID), eq(PRODUCT_ID), any(), any(), eq(USER_ID)))
            .thenReturn(1);

        PackCheckBo bo = new PackCheckBo();
        bo.setProductId(PRODUCT_ID);
        bo.setLocationId(LOCATION_ID);
        bo.setCheckStock(new BigDecimal("7"));
        bo.setRemark("ut-packcheck");

        Long flowId = service.packCheck(bo);
        assertThat(flowId).isEqualTo(50001L);

        ArgumentCaptor<StockFlow> cap = ArgumentCaptor.forClass(StockFlow.class);
        verify(stockFlowMapper, times(1)).insert(cap.capture());
        StockFlow f = cap.getValue();
        assertThat(f.getInoutType()).isEqualTo("OT");
        assertThat(f.getFlowType()).isEqualTo("check_out");
        assertThat(f.getChangeNum()).isEqualByComparingTo("-3");
        assertThat(f.getChangeQuantity()).isEqualByComparingTo("3");
        // 货架校准到实盘绝对值 7 + 结果自动判异常(2)
        verify(locationStockMapper, times(1))
            .setStockAfterCheck(eq(LOCATION_ID), eq(PRODUCT_ID), eq(new BigDecimal("7")), eq(2), eq(USER_ID));
    }

}
