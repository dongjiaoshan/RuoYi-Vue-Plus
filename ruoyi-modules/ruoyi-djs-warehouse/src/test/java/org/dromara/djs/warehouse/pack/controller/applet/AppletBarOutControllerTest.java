package org.dromara.djs.warehouse.pack.controller.applet;

import org.dromara.common.core.domain.R;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutPickupBo;
import org.dromara.djs.warehouse.cut.domain.vo.BarPickupItemVo;
import org.dromara.djs.warehouse.cut.service.IPigCutRecordService;
import org.dromara.djs.warehouse.pack.domain.bo.WarehouseOutBo;
import org.dromara.djs.warehouse.pack.domain.bo.WhiteBarOutBo;
import org.dromara.djs.warehouse.pack.mapper.ProductProductionMapper;
import org.dromara.djs.warehouse.pack.service.IProductProductionService;
import org.dromara.djs.warehouse.product.domain.ProductInhouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AppletBarOutController} happy-path 单测（V6 row103）。
 *
 * <p>核心断言：mp 白条出库端点全部「薄委托」到 admin 白条领用页复用的同一批 ServiceImpl 方法，
 * 没有任何 mp 专属业务分支（三去向行为与后台一致的前提）。纯 Mockito，不起 Spring 上下文。</p>
 *
 * @author djs
 * @since V6-R103
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AppletBarOutController mp 白条出库委托单元测试")
class AppletBarOutControllerTest {

    @Mock private IPigCutRecordService pigCutService;
    @Mock private IProductProductionService productionService;
    @Mock private ProductProductionMapper productProductionMapper;

    private AppletBarOutController controller;

    @BeforeEach
    void setup() {
        controller = new AppletBarOutController(pigCutService, productionService, productProductionMapper);
    }

    @Test
    @DisplayName("白条卡列表复用 admin 同源 queryPickupItems（含到场时间/到场重量）")
    void itemsDelegatesToQueryPickupItems() {
        BarPickupItemVo vo = new BarPickupItemVo();
        vo.setEarNo("E0001");
        vo.setArriveTime(new java.util.Date(1_700_000_000_000L));
        vo.setArriveWeight(new java.math.BigDecimal("101.500"));
        when(pigCutService.queryPickupItems()).thenReturn(List.of(vo));

        R<List<BarPickupItemVo>> r = controller.items();

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).hasSize(1);
        assertThat(r.getData().get(0).getArriveTime()).isNotNull();
        assertThat(r.getData().get(0).getArriveWeight()).isEqualByComparingTo("101.500");
        verify(pigCutService).queryPickupItems();
    }

    @Test
    @DisplayName("门店发货可选门店透传 selectWhiteBarShipStores（无需求 → 空 List）")
    void shipStoresPassthrough() {
        when(productProductionMapper.selectWhiteBarShipStores())
            .thenReturn(List.of(Map.of("storeId", "9301000000000001", "storeName", "大冶门店", "demandQty", 3)));

        R<List<Map<String, Object>>> r = controller.shipStores();

        assertThat(r.getData()).hasSize(1);
        assertThat(r.getData().get(0)).containsEntry("storeName", "大冶门店");
        verify(productProductionMapper).selectWhiteBarShipStores();
    }

    @Test
    @DisplayName("整只兜底卡来源列表透传 listSourceForWhiteBar")
    void sourcesPassthrough() {
        when(productionService.listSourceForWhiteBar()).thenReturn(List.of(new ProductInhouse()));

        assertThat(controller.sources().getData()).hasSize(1);
        verify(productionService).listSourceForWhiteBar();
    }

    @Test
    @DisplayName("出库去向=分割车间 → submitPickup（出库人 operatorId 原样透传，不在 controller 改写）")
    void outToCutDelegatesToSubmitPickup() {
        PigCutPickupBo bo = new PigCutPickupBo();
        bo.setBarInfoId(11L);
        bo.setInhouseId(22L);
        bo.setOperatorId(33L);
        when(pigCutService.submitPickup(any())).thenReturn(2001L);

        R<Long> r = controller.outToCut(bo);

        assertThat(r.getData()).isEqualTo(2001L);
        verify(pigCutService).submitPickup(bo);
        assertThat(bo.getOperatorId()).isEqualTo(33L);
    }

    @Test
    @DisplayName("出库去向=门店发货 → submitWhiteBarOut")
    void outToShipDelegatesToSubmitWhiteBarOut() {
        WhiteBarOutBo bo = new WhiteBarOutBo();
        when(productionService.submitWhiteBarOut(any())).thenReturn(2002L);

        R<Long> r = controller.outToShip(bo);

        assertThat(r.getData()).isEqualTo(2002L);
        verify(productionService).submitWhiteBarOut(bo);
    }

    @Test
    @DisplayName("出库去向=仓库出库 → submitWarehouseOut")
    void outToWarehouseDelegatesToSubmitWarehouseOut() {
        WarehouseOutBo bo = new WarehouseOutBo();
        bo.setOutDest("mine");
        when(productionService.submitWarehouseOut(any())).thenReturn(2003L);

        R<Long> r = controller.outToWarehouse(bo);

        assertThat(r.getData()).isEqualTo(2003L);
        verify(productionService).submitWarehouseOut(bo);
    }
}
