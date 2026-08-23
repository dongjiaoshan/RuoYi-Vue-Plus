package org.dromara.djs.warehouse.vegout.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 毛菜间出库-可选产品行（admin row187 新增抽屉左侧列表）。
 *
 * <p>数据源 = 毛菜鲜品库（L0006）里 belong_type='vegetable' 的库存行，
 * 一行一个「产品 × 地块」篮（location_stock 自带 plot_id）。</p>
 *
 * @author djs
 */
@Data
public class VegOutCandidateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 库存行 id（提交时按行出库）。 */
    private Long stockId;

    /** 产品 id。 */
    private Long productId;

    /**
     * 产品业务编号（{@code t_warehouse_product_info.product_id}，用户手填的产品编码，不是主键）。
     *
     * <p>V6 row108：右侧「已选产品」与打印单按它把同一产品的多个地块篮合并成一条
     * （甲方原文「只按产品编号进行累计」）。提交仍按 {@link #stockId} 逐行走。</p>
     */
    private String productCode;

    /** 产品名称。 */
    private String productName;

    /** 产品规格。 */
    private String productSpec;

    /** 库存重量（kg）。 */
    private BigDecimal stockWeight;

    /** 计量单位。 */
    private String productUnit;

    /** 地块 id（可空：非按地块入库的行）。 */
    private Long plotId;

    /** 地块编号（冗余展示；plotId 为空时为 null）。 */
    private String plotCode;

    /** 地块名称（admin row99「地块」列展示值；plotId 为空时为 null）。 */
    private String plotName;

    /**
     * 三期标识（{@code location_stock.third_phase}，1 = 三期）。
     *
     * <p>三期货没有真实地块（plot_id 为 NULL），「地块」列靠这个标识显示「三期」——
     * 与库存查询 / 出入库记录三页共用的 {@code utils/plotTag.formatPlotLabel} 同一口径。</p>
     */
    private Integer thirdPhase;

    /** 产品业态（djs_belong_type）：vegetable / dry_good / egg / other，前端按它分组或判断是否有地块。 */
    private String belongType;

    /** 产品销售价格（row191，产品主数据 sale_price）：新增页「销售单价」的默认值，用户可改。 */
    private java.math.BigDecimal salePrice;
}
