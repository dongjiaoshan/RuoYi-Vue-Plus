package org.dromara.djs.warehouse.vegout.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 毛菜间出库-可选产品行（admin row187 新增抽屉左侧列表）。
 *
 * <p>数据源 = 可出库库位白名单里 {@code product_attr=2}（原材料）的库存行，一行一个库存篮
 * （{@code t_warehouse_location_stock} 一行）。果蔬篮带 {@code plot_id}、猪肉篮带 {@code ear_no}，
 * 干货 / 蛋类 / 其他两者都没有 —— 前端三个 tab 的第三列就按这个差异切换。</p>
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
     * 猪只耳号（{@code location_stock.ear_no}）：猪肉 tab 用它替代「地块」列。
     *
     * <p>分割间按「部位 × 耳号」建篮时写入（{@code PigCutRecordServiceImpl}）；
     * 外购白条没有耳号，此处为空、列显示占位符。</p>
     */
    private String earNo;

    /**
     * 存储仓库名称（{@code t_warehouse_location_info.location_name}）。
     *
     * <p>是<b>这个篮子实际所在的库位</b>，工人照它去哪个库拿货 —— 不是产品主数据上配置的
     * {@code store_location_id}（那只是建议落点，同一产品的篮子完全可能不在那个库）。</p>
     */
    private String locationName;

    /**
     * 三期标识（{@code location_stock.third_phase}，1 = 三期）。
     *
     * <p>三期货没有真实地块（plot_id 为 NULL），「地块」列靠这个标识显示「三期」——
     * 与库存查询 / 出入库记录三页共用的 {@code utils/plotTag.formatPlotLabel} 同一口径。</p>
     */
    private Integer thirdPhase;

    /** 产品业态（djs_belong_type）：vegetable / pork / dry_good / egg / other，前端按它分 tab。 */
    private String belongType;

    /** 产品销售价格（row191，产品主数据 sale_price）：新增页「销售单价」的默认值，用户可改。 */
    private java.math.BigDecimal salePrice;
}
