package org.dromara.djs.warehouse.flow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 种子库存聚合 VO（FIX-PLANT-SEED-001）。
 *
 * <p>按 {@code product_id} 聚合 {@code t_warehouse_location_stock}（SUM(product_stock)），
 * 用于 mp 端「播种」首页「种子领用」tab —— 展示 {@code belong_type='seed'} 的种子类商品
 * （蔬菜种子 / 粮食种子 / 果树种子 等）的全场总库存 + 当前登录人今日已领 / 已退。</p>
 *
 * <p>种子领用复用 WMS 商品库（_open-issues 决策 a：不新建种子表），与饲料同款 mat 领用链路，
 * 录入走 {@code /applet/warehouse/mat/pick|return} + matType=seed。</p>
 *
 * @author djs
 * @since FIX-PLANT-SEED-001
 */
@Data
public class SeedStockVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品 ID（雪花主键，{@code t_warehouse_product_info.id}）。 */
    private Long productId;

    /** 业务码（如 PROD-SEED-VEG-01）。 */
    private String productCode;

    /** 产品名（蔬菜种子 / 粮食种子 / 果树种子 / 草本种子 / 其他种子）。 */
    private String productName;

    /** 单位（种子按 g / 粒）。 */
    private String productUnit;

    /** 全场总库存（SUM(product_stock)，跨库位聚合）。 */
    private BigDecimal totalStock;

    /** 当前登录人今日已领（当日 + 本产品，无流水时 0）。 */
    private BigDecimal todayPicked;

    /** 当前登录人今日已退（当日 + 本产品，无流水时 0）。 */
    private BigDecimal todayReturned;

}
