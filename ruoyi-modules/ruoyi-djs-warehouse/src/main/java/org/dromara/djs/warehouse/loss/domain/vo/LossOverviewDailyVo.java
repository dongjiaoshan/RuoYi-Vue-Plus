package org.dromara.djs.warehouse.loss.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 损耗总览每日汇总视图对象（WMS-LOSS-OVERVIEW-001，仓库-admin 行63）。
 *
 * <p>compute-on-read：直接 over {@code t_warehouse_loss_flow} 按 {@code DATE(loss_date)} 聚合，
 * 不建汇总表。每行 = 某自然日 + 当日损耗品种数（明细按产品编码去重）。明细在
 * {@link LossOverviewDetailVo}（按日下钻）。</p>
 *
 * @author djs
 * @since WMS-LOSS-OVERVIEW-001
 */
@Data
public class LossOverviewDailyVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 损耗日期（yyyy-MM-dd）。 */
    private String lossDate;

    /** 当日损耗品种数 = 明细 DISTINCT product_code（燎毛损耗无编码，不计入）。 */
    private Integer productCount;
}
