package org.dromara.djs.store.manage.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店管理明细 - mapper 原始行：某产品在某月份区间的三个量（V6-R209 逐产品环比的基数）。
 *
 * <p>只承载数字，不带产品名 / 规格 / 单位——它只用来当环比分母，展示字段全取本月那一行。</p>
 *
 * @author djs
 * @since V6-R209
 */
@Data
public class StoreManageProductQtyRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 ID。 */
    private Long productId;

    /** 该区间需求量合计。 */
    private BigDecimal demandQty;

    /** 该区间销售量合计。 */
    private BigDecimal saleQty;

    /** 该区间退回量合计。 */
    private BigDecimal returnQty;
}
