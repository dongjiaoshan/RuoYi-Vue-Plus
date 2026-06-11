package org.dromara.djs.warehouse.pack.controller;

import org.dromara.common.core.domain.R;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutDoneBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutOutBo;
import org.dromara.djs.warehouse.cut.domain.bo.PigCutPickupBo;
import org.dromara.djs.warehouse.cut.domain.query.PigCutRecordQuery;
import org.dromara.djs.warehouse.cut.domain.vo.BarInfoVo;
import org.dromara.djs.warehouse.cut.domain.vo.PigCutRecordVo;
import org.dromara.djs.warehouse.cut.service.IPigCutRecordService;
import org.dromara.djs.warehouse.pack.domain.bo.DryPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.GiftPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.VegPackBo;
import org.dromara.djs.warehouse.pack.domain.bo.WhiteBarOutBo;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WarehousePackEntryController} happy-path 单测（A5）。
 *
 * <p>验证 admin 录入端点全部「薄委托」到复用的 mp ServiceImpl 业务方法、并正确 {@code R.ok} 包装：</p>
 * <ol>
 *   <li>肉品/其他打包 → submitDryPack；礼盒 → submitGiftPack；果蔬 → submitVegPack（返新 production.id）</li>
 *   <li>分割 cutOut → submitCutOut（void）；白条完成分割 cutDone → submitCutDone（void）</li>
 *   <li>白条领用 pickup → submitPickup（返 cut_record.id）；发货领用 whiteBarOut → submitWhiteBarOut</li>
 *   <li>来源/白条列表 GET 端点透传 service 查询结果</li>
 * </ol>
 *
 * <p>纯 Mockito，不起 Spring 上下文。</p>
 *
 * @author djs
 * @since A5
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WarehousePackEntryController admin 录入委托单元测试")
class WarehousePackEntryControllerTest {

    @Mock private IProductProductionService productionService;
    @Mock private IPigCutRecordService pigCutService;

    private WarehousePackEntryController controller;

    @BeforeEach
    void setup() {
        controller = new WarehousePackEntryController(productionService, pigCutService);
    }

    @Test
    @DisplayName("肉品/其他打包委托 submitDryPack 并返新 id")
    void packDryDelegates() {
        DryPackBo bo = new DryPackBo();
        when(productionService.submitDryPack(any())).thenReturn(1001L);

        R<Long> r = controller.packDry(bo);

        assertThat(r.getCode()).isEqualTo(200);
        assertThat(r.getData()).isEqualTo(1001L);
        verify(productionService).submitDryPack(bo);
    }

    @Test
    @DisplayName("礼盒打包委托 submitGiftPack")
    void packGiftDelegates() {
        GiftPackBo bo = new GiftPackBo();
        when(productionService.submitGiftPack(any())).thenReturn(1002L);

        R<Long> r = controller.packGift(bo);

        assertThat(r.getData()).isEqualTo(1002L);
        verify(productionService).submitGiftPack(bo);
    }

    @Test
    @DisplayName("果蔬打包委托 submitVegPack")
    void packVegDelegates() {
        VegPackBo bo = new VegPackBo();
        when(productionService.submitVegPack(any())).thenReturn(1003L);

        R<Long> r = controller.packVeg(bo);

        assertThat(r.getData()).isEqualTo(1003L);
        verify(productionService).submitVegPack(bo);
    }

    @Test
    @DisplayName("分割出库称重委托 submitCutOut（void）")
    void cutOutDelegates() {
        PigCutOutBo bo = new PigCutOutBo();

        R<Void> r = controller.cutOut(bo);

        assertThat(r.getCode()).isEqualTo(200);
        verify(pigCutService).submitCutOut(bo);
    }

    @Test
    @DisplayName("白条完成分割委托 submitCutDone（void）")
    void cutDoneDelegates() {
        PigCutDoneBo bo = new PigCutDoneBo();

        R<Void> r = controller.cutDone(bo);

        assertThat(r.getCode()).isEqualTo(200);
        verify(pigCutService).submitCutDone(bo);
    }

    @Test
    @DisplayName("白条领用委托 submitPickup 并返 cut_record.id")
    void pickupDelegates() {
        PigCutPickupBo bo = new PigCutPickupBo();
        when(pigCutService.submitPickup(any())).thenReturn(2001L);

        R<Long> r = controller.pickup(bo);

        assertThat(r.getData()).isEqualTo(2001L);
        verify(pigCutService).submitPickup(bo);
    }

    @Test
    @DisplayName("发货领用委托 submitWhiteBarOut")
    void whiteBarOutDelegates() {
        WhiteBarOutBo bo = new WhiteBarOutBo();
        when(productionService.submitWhiteBarOut(any())).thenReturn(2002L);

        R<Long> r = controller.whiteBarOut(bo);

        assertThat(r.getData()).isEqualTo(2002L);
        verify(productionService).submitWhiteBarOut(bo);
    }

    @Test
    @DisplayName("来源/白条 GET 端点透传 service 查询结果")
    void sourceAndBarsPassthrough() {
        when(productionService.listSourceForDry()).thenReturn(List.of(new ProductInhouse()));
        when(productionService.listSourceForVeg()).thenReturn(List.of(new ProductInhouse()));
        when(productionService.listSourceForWhiteBar()).thenReturn(List.of(new ProductInhouse()));
        when(pigCutService.queryAvailableBars()).thenReturn(List.of(new BarInfoVo()));

        assertThat(controller.sourceDry().getData()).hasSize(1);
        assertThat(controller.sourceVeg().getData()).hasSize(1);
        assertThat(controller.sourceWhiteBar().getData()).hasSize(1);
        assertThat(controller.availableBars().getData()).hasSize(1);
    }

    @Test
    @DisplayName("cuttableList 合并 picked + cutting 两态分割记录")
    void cuttableListMergesPickedAndCutting() {
        PigCutRecordVo picked = new PigCutRecordVo();
        picked.setCutStatus("picked");
        PigCutRecordVo cutting = new PigCutRecordVo();
        cutting.setCutStatus("cutting");
        when(pigCutService.queryList(any(PigCutRecordQuery.class)))
            .thenReturn(List.of(picked))
            .thenReturn(List.of(cutting));

        R<List<PigCutRecordVo>> r = controller.cuttableList();

        assertThat(r.getData()).hasSize(2);
    }
}
