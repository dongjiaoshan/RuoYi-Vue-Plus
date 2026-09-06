package org.dromara.djs.store.manage.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 门店管理月度看板 - mapper 原始聚合行（belong_type × product_unit × 合计量）。
 *
 * <p>三个指标（需求 / 销售 / 退回）共用本行结构，service 负责把 belong_type 归并成 4 业态桶。</p>
 *
 * @author djs
 * @since MGMT-MP-STORE-MONTH-001
 */
@Data
public class StoreManageQtyRowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品归属业态（字典 djs_belong_type）。 */
    private String belongType;

    /** 计量单位（产品主数据 product_unit）。 */
    private String unit;

    /** 该 (业态, 单位) 组合的当月合计量。 */
    private BigDecimal qty;

}
