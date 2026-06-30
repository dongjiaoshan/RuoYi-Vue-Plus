package org.dromara.djs.store.demand.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.store.demand.domain.bo.StoreDemandBatchBo;
import org.dromara.djs.warehouse.demand.core.enums.DemandStatus;
import org.dromara.djs.warehouse.demand.domain.DemandManage;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.mapper.DemandManageMapper;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
import org.dromara.djs.warehouse.product.domain.ProductInfo;
import org.dromara.djs.warehouse.product.mapper.ProductInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreDemandServiceImpl} 单测（STR-DEMAND-001）。
 *
 * <p>覆盖薄封装层核心语义：门店发起需求 = insertByBo（warehouse 侧直接落 SUBMITTED），
 * 不再二次 transition(SUBMIT)（否则对已 SUBMITTED 的单子触发 SUBMIT = 非法状态转移）。验证：</p>
 * <ol>
 *   <li>createStoreDemand happy：返回 warehouse insertByBo 的 id；insertByBo 恰调用一次</li>
 *   <li>BO.id 被强制清空（门店端创建专用，不走编辑路径）</li>
 *   <li>batchCreate：逐项补冗余字段 + mailing 标记 + 逐条 insertByBo</li>
 * </ol>
 *
 * <p>不验证状态机 / 编码 / 业态校验细节——那是 warehouse service 的职责（已被 DemandManageServiceImplTest /
 * DemandStateMachine 测覆盖），本类只验证封装顺序与 store_id 落库。</p>
 *
 * @author djs
 * @since STR-DEMAND-001
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StoreDemandServiceImpl 门店发起需求单测")
class StoreDemandServiceImplTest {

    @Mock
    private IDemandManageService demandManageService;

    @Mock
    private ProductInfoMapper productInfoMapper;

    @Mock
    private DemandManageMapper demandManageMapper;

    @Mock
    private org.dromara.djs.warehouse.pack.service.IProductProductionService productProductionService;

    @InjectMocks
    private StoreDemandServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeEach
    void setUp() {
        loginHelperMock = Mockito.mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(2001L);
    }

    @AfterEach
    void tearDown() {
        loginHelperMock.close();
    }

    private DemandManageBo buildBo() {
        DemandManageBo bo = new DemandManageBo();
        bo.setDemandDate(LocalDate.now());
        bo.setStoreId(5001L);
        bo.setProductId(8001L);
        bo.setProductName("白条猪");
        bo.setProductType("white_bar");
        bo.setDemandQuantity(new BigDecimal("3"));
        bo.setProductUnit("头");
        return bo;
    }

    @Test
    @DisplayName("createStoreDemand：仅 insertByBo（已落 SUBMITTED），不再二次 transition(SUBMIT)")
    void createStoreDemandSubmitsImmediately() {
        DemandManageBo bo = buildBo();
        when(demandManageService.insertByBo(bo)).thenReturn(9001L);

        Long id = service.createStoreDemand(bo);

        assertThat(id).isEqualTo(9001L);
        // warehouse insertByBo 直接落 SUBMITTED，恰调用一次
        verify(demandManageService, times(1)).insertByBo(bo);
    }

    @Test
    @DisplayName("createStoreDemand：强制清空 BO.id（门店端创建专用，不误更新已有记录）")
    void createStoreDemandClearsId() {
        DemandManageBo bo = buildBo();
        bo.setId(7777L); // 误带 id
        when(demandManageService.insertByBo(bo)).thenReturn(9002L);

        service.createStoreDemand(bo);

        assertThat(bo.getId()).isNull();
        verify(demandManageService, times(1)).insertByBo(bo);
    }

    @Test
    @DisplayName("batchCreate：多产品整单逐条建需求，补冗余字段 + mailing 标记 + 逐条 insertByBo")
    void batchCreateBuildsMultipleDemands() {
        ProductInfo p1 = new ProductInfo();
        p1.setId(8001L);
        p1.setProductName("白条(半扇)");
        p1.setProductSpec("半头");
        p1.setProductUnit("头");
        ProductInfo p2 = new ProductInfo();
        p2.setId(8002L);
        p2.setProductName("猪肉礼盒");
        p2.setProductSpec("250g");
        p2.setProductUnit("盒");
        when(productInfoMapper.selectById(8001L)).thenReturn(p1);
        when(productInfoMapper.selectById(8002L)).thenReturn(p2);
        when(demandManageService.insertByBo(any(DemandManageBo.class))).thenReturn(9101L, 9102L);

        StoreDemandBatchBo batch = new StoreDemandBatchBo();
        batch.setStoreId(5001L);
        batch.setDemandRemark("整单备注");
        StoreDemandBatchBo.Item i1 = new StoreDemandBatchBo.Item();
        i1.setProductId(8001L);
        i1.setProductType("white_bar");
        i1.setDemandQuantity(new BigDecimal("1"));
        i1.setMailing(false);
        StoreDemandBatchBo.Item i2 = new StoreDemandBatchBo.Item();
        i2.setProductId(8002L);
        i2.setProductType("gift_box");
        i2.setDemandQuantity(new BigDecimal("3"));
        i2.setMailing(true);
        batch.setItems(List.of(i1, i2));

        int created = service.batchCreate(batch);

        assertThat(created).isEqualTo(2);
        ArgumentCaptor<DemandManageBo> cap = ArgumentCaptor.forClass(DemandManageBo.class);
        verify(demandManageService, times(2)).insertByBo(cap.capture());
        List<DemandManageBo> bos = cap.getAllValues();
        // 第 1 条：白条，门店自取
        assertThat(bos.get(0).getProductName()).isEqualTo("白条(半扇)");
        assertThat(bos.get(0).getProductUnit()).isEqualTo("头");
        assertThat(bos.get(0).getDemandType()).isEqualTo("store");
        assertThat(bos.get(0).getDemandRemark()).isEqualTo("整单备注");
        // 第 2 条：礼盒，个人邮寄
        assertThat(bos.get(1).getProductName()).isEqualTo("猪肉礼盒");
        assertThat(bos.get(1).getDemandType()).isEqualTo("mailing");
    }

    @Test
    @DisplayName("receive：已发货态（PARTIAL_SHIPPED）门店确认到店 → patch received_time/received_by")
    void receiveShippedDemand() {
        DemandManage demand = new DemandManage();
        demand.setId(9001L);
        demand.setDemandNo("DEMWB20260101001");
        demand.setDemandStatus(DemandStatus.PARTIAL_SHIPPED.name());
        when(demandManageMapper.selectById(9001L)).thenReturn(demand);

        service.receive(9001L);

        ArgumentCaptor<DemandManage> cap = ArgumentCaptor.forClass(DemandManage.class);
        verify(demandManageMapper, times(1)).updateById(cap.capture());
        DemandManage patch = cap.getValue();
        assertThat(patch.getId()).isEqualTo(9001L);
        assertThat(patch.getReceivedTime()).isNotNull();
        assertThat(patch.getReceivedBy()).isEqualTo(2001L);
    }

    @Test
    @DisplayName("receive：已发货态（COMPLETED）门店确认到店 → patch received_time/received_by")
    void receiveCompletedDemand() {
        DemandManage demand = new DemandManage();
        demand.setId(9003L);
        demand.setDemandStatus(DemandStatus.COMPLETED.name());
        when(demandManageMapper.selectById(9003L)).thenReturn(demand);

        service.receive(9003L);

        verify(demandManageMapper, times(1)).updateById(any(DemandManage.class));
    }

    @Test
    @DisplayName("receive：未发货态拒绝（如 CONFIRMED / SUBMITTED — 原型仅「已发货」行可确认收货）")
    void receiveRejectNotShipped() {
        DemandManage confirmed = new DemandManage();
        confirmed.setId(9002L);
        confirmed.setDemandStatus(DemandStatus.CONFIRMED.name());
        when(demandManageMapper.selectById(9002L)).thenReturn(confirmed);

        assertThatThrownBy(() -> service.receive(9002L)).isInstanceOf(ServiceException.class);
        verify(demandManageMapper, never()).updateById(any(DemandManage.class));
    }
}
