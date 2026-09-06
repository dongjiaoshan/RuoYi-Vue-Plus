package org.dromara.djs.store.manage.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店管理月度看板 - 单指标 + 环比（MGMT-MP-STORE-MONTH-001）。
 *
 * <p>三指标（需求量 / 销售量 / 退回量）共用本结构。</p>
 *
 * <p>{@code hasBase=false} 表示<b>上月没有可比基数</b>（上月该口径为 0 或无记录）。甲方口径：
 * 「没有上个月数据的时候，比率显示为黑色的 0.00%」——所以此时 {@code mom} 固定 0.00，
 * 前端据 {@code hasBase} 把颜色降为中性黑，而不是当成"环比持平"的绿/红。
 * 真的持平（上月有基数且相等）时 {@code hasBase=true} 且 {@code mom=0.00}，两种 0.00 必须分得开。</p>
 *
 * @author djs
 * @since MGMT-MP-STORE-MONTH-001
 */
@Data
public class StoreManageMetricVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 本月合计值（scale=3，与业务表 decimal(12,3) 同精度）。 */
    private BigDecimal value;

    /** 环比百分比（scale=2，如 12.50 表示 +12.50%）；无基数时固定 0.00。 */
    private BigDecimal mom;

    /** 上月是否有可比基数（false → 前端渲染黑色 0.00%）。 */
    private Boolean hasBase;

}
