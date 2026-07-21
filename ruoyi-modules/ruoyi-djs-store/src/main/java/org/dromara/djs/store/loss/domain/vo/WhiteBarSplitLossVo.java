package org.dromara.djs.store.loss.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 当日某门店白条分割损耗信息（门店盘点抽屉「当日白条分割损耗」展示）。
 *
 * <p>口径与 {@code white_bar_split_loss} 定时任务一致：
 * {@code splitLoss = max(0, arriveWeight − splitProduceWeight − returnReceivedWeight)}。
 * 分割产出重来自门店端分割白条产出的原材料 inhouse（{@code source='store'}，StoreSplit 写）；
 * 退回入库重来自门店退货入库流水 {@code t_store_return}（门店退回仓库、已入库、白条退回产品字典），
 * 非门店盘点台账的退回列。</p>
 *
 * @author djs
 * @since DENGBO-R30
 */
@Data
public class WhiteBarSplitLossVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当日白条到店重量 kg（white_bar 发货 ship_quantity 之和）；>0 才显示分割损耗块。 */
    private BigDecimal arriveWeight;

    /** 当日白条退回产品入库重 kg（t_store_return 门店退回仓库、已入库、白条退回产品字典之和）。 */
    private BigDecimal returnReceivedWeight;

    /** row34：当日门店白条分割产出重 kg（source='store' 分割产 inhouse product_weight 之和，StoreSplit 写）。 */
    private BigDecimal splitProduceWeight;

    /** 当日白条分割损耗 kg = max(0, arriveWeight − splitProduceWeight − returnReceivedWeight)。 */
    private BigDecimal splitLoss;
}
