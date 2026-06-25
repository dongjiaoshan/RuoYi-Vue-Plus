package org.dromara.djs.warehouse.pack.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 礼盒打包页顶部「可用礼盒组件」一项（Kevin 2026-06-25）。
 *
 * <p>= 肉品/果蔬/其他产品打包时「发送位置=礼盒」产出、尚未被礼盒打包消耗的生产产品
 * （{@code product_production.deliver_dest='gift' AND produce_quantity>0}），按 {@code product_id} 聚合。
 * 礼盒打包消耗后该产品可用量相应减少（前端 @submitted 后重拉刷新）。</p>
 *
 * @author djs
 */
@Data
public class GiftComponentStockVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 生产产品 id（FK → t_warehouse_product_info.id）。 */
    private Long productId;

    /** 生产产品名称。 */
    private String productName;

    /** 单位（取生产产品打包记录的 product_unit）。 */
    private String productUnit;

    /** 当前可用于礼盒打包的合计量（= 该产品 deliver_dest='gift' 未消耗生产产品 SUM(produce_quantity)）。 */
    private BigDecimal availableQty;
}
