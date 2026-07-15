package org.dromara.djs.common.medicine.api;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 药品商品行（跨模块 facade 传输对象）。
 *
 * <p>药品即仓库商品（{@code t_warehouse_product_info.buy_class='medicine'}），
 * 养殖 med 域经 {@link MedicineStockProvider#listMedicineProducts} 消费仓库药品清单，
 * 不直接依赖 warehouse 领域类（依赖方向 warehouse → common）。</p>
 *
 * @author djs
 */
@Data
public class MedicineProductDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 药品商品 id（{@code t_warehouse_product_info.id}） */
    private Long id;

    /** 药品名（product_name） */
    private String name;

    /** 单位（product_unit） */
    private String unit;

    /** 规格（product_spec） */
    private String spec;

    /** 当前库存合计（跨库位 SUM(product_stock)，无库存行按 0） */
    private BigDecimal stock;

    /** 展示图 public URL（{@code COALESCE(product_thumb, image_oss_id)}，mp 领用卡片展示） */
    private String imageUrl;

    /**
     * 今日已领（{@code t_warehouse_stock_flow} 当日 dept_pick_out SUM，全场口径，与物资领用卡同源）。
     * mp 药品领用卡下排「今日已领」，与物资领用 stock-card 三量对齐（row129）。
     */
    private BigDecimal todayPicked;

    /** 今日退回（当日 pick_return_in SUM，全场口径）。 */
    private BigDecimal todayReturned;

    /** 今日损耗（当日 loss SUM，全场口径）。 */
    private BigDecimal todayLoss;

}
