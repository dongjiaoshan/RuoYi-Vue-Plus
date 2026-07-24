package org.dromara.djs.store.loss.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 当日某门店白条分割损耗信息（门店盘点抽屉「当日白条分割损耗」展示）。
 *
 * <p>口径与 {@code white_bar_split_loss} 定时任务一致：
 * {@code splitTotalWeight = 白条在门店分割下来的产品总重 = 当日该店盘点各白条部位（白条退回字典产品）入库量之和}；
 * {@code splitLoss = max(0, arriveWeight − splitTotalWeight)}。</p>
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

    /** 当日白条分割产品总重 kg = 当日该店盘点各白条部位入库量之和（白条在门店分割下来的产品总重）。 */
    private BigDecimal splitTotalWeight;

    /** 当日白条分割损耗 kg = max(0, arriveWeight − splitTotalWeight)。 */
    private BigDecimal splitLoss;
}
