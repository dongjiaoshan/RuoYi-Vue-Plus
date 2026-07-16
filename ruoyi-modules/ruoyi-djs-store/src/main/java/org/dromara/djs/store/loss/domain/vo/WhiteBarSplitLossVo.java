package org.dromara.djs.store.loss.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 当日某门店白条分割损耗信息（门店盘点抽屉「当日白条分割损耗」展示）。
 *
 * <p>口径与 {@code white_bar_split_loss} 定时任务一致：
 * {@code splitLoss = max(0, arriveWeight − returnReceivedWeight)}。
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

    /** 当日白条分割损耗 kg = max(0, arriveWeight − returnReceivedWeight)。 */
    private BigDecimal splitLoss;
}
