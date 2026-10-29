package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订货单打印明细行（V7-R8）：一行 = 该门店该日的一个需求产品（同产品多条需求已合并）。
 *
 * @author djs
 * @since V7-R8
 */
@Data
public class DemandPrintItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 FK（{@code t_warehouse_product_info.id}）。 */
    private Long productId;

    /** 产品名称（需求行下单时定格的冗余名）。 */
    private String productName;

    /** 产品规格。 */
    private String productSpec;

    /** 需求量（同产品多条需求求和）。 */
    private BigDecimal demandQuantity;

    /** 单位（需求行冗余单位；白条展示「头」由前端按 belongType 处理，与需求管理列表同一套规则）。 */
    private String productUnit;

    /** 产品类别（{@code djs_belong_type}），前端据此判断白条单位 / 数量格式。 */
    private String belongType;

    /** 备注 = 需求备注（同产品多条需求的非空备注去重后以「；」拼接）。 */
    private String demandRemark;
}
