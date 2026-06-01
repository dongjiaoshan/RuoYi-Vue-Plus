package org.dromara.djs.store.demand.service.impl;

import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.djs.warehouse.demand.core.enums.DemandEvent;
import org.dromara.djs.warehouse.demand.domain.bo.DemandManageBo;
import org.dromara.djs.warehouse.demand.service.IDemandManageService;
import org.dromara.djs.warehouse.demand.service.IDemandStatusService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StoreDemandServiceImpl} 单测（STR-DEMAND-001）。
 *
 * <p>覆盖薄封装层核心语义：门店发起需求 = insertByBo（DRAFT）+ transition(SUBMIT) 两步原子，
 * 落库后立即推到 SUBMITTED（跳过 DRAFT）。验证：</p>
 * <ol>
 *   <li>createStoreDemand happy：返回 warehouse insertByBo 的 id；id 被 SUBMIT transition 用一次</li>
 *   <li>BO.id 被强制清空（门店端创建专用，不走编辑路径）</li>
 *   <li>operator 取 LoginHelper 当前 userId 透传给状态机</li>
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
    private IDemandStatusService demandStatusService;

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
    @DisplayName("createStoreDemand：insertByBo → transition(SUBMIT) 两步，返回新建 id + 推到 SUBMITTED")
    void createStoreDemandSubmitsImmediately() {
        DemandManageBo bo = buildBo();
        when(demandManageService.insertByBo(bo)).thenReturn(9001L);

        Long id = service.createStoreDemand(bo);

        assertThat(id).isEqualTo(9001L);
        // 第 1 步：warehouse 落库
        verify(demandManageService, times(1)).insertByBo(bo);
        // 第 2 步：对同一 id 触发 SUBMIT，operator 取当前 userId
        verify(demandStatusService, times(1)).transition(eq(9001L), eq(DemandEvent.SUBMIT), eq(2001L), eq("门店发起"));
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
        verify(demandStatusService, times(1)).transition(eq(9002L), eq(DemandEvent.SUBMIT), eq(2001L), eq("门店发起"));
    }
}
