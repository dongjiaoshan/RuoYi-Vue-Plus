package org.dromara.djs.warehouse.flow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 物资领用「待领产品卡」VO（FIX-WMS-MATISSUE-001）。
 *
 * <p>口径：以某业态（{@code belong_type}）的产品为粒度，LEFT JOIN
 * {@code t_warehouse_location_stock} 聚合当前库存；并按当前登录人 + 今日子查询出
 * 已领 / 已退 / 已损（与「今日数据卡」「最大可领 / 退 / 损」上限计算同源）。</p>
 *
 * <p>原型卡片字段：缩略图 + 名称 + 右上「库存：N 单位」+「今日已领 / 今日退回 / 今日损耗」。
 * 与 {@link PackingItemVo}（仅库存 + 盘点日期，全租户口径）区别：本 VO 多了
 * <b>当前操作人今日三量</b>（领 / 退 / 损），直接驱动卡片与点入表单的上限。</p>
 *
 * <p>跨层契约：{@code productId} / {@code locationId} 是 snowflake，前端按 string 处理。</p>
 *
 * @author djs
 * @since FIX-WMS-MATISSUE-001
 */
@Data
public class MatIssueItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品主键（snowflake；前端按 string 处理，点卡进表单时作 productId）。
     */
    private Long productId;

    /**
     * 产品业务码（如 PROD001）。
     */
    private String productCode;

    /**
     * 产品名称（卡片主标题，如「番茄」「包材名称1」）。
     */
    private String productName;

    /**
     * 单位（个 / 头 / Kg 等）。
     */
    private String productUnit;

    /**
     * 缩略图 OSS ID（单张；卡片左侧图，可能为空）。
     */
    private String productThumb;

    /**
     * 当前库存（跨库位 SUM；指定 locationId 时为该库位库存；无库存行为 0）。
     */
    private BigDecimal currentStock;

    /**
     * 默认库位 ID（该产品库存最多的库位；点卡进表单时作默认 locationId，可能为空）。
     */
    private Long defaultLocationId;

    /**
     * 当前登录人今日已领（pick_out SUM；驱动「今日已领」+ 退/损上限）。
     */
    private BigDecimal todayPicked;

    /**
     * 当前登录人今日已退（return_in SUM）。
     */
    private BigDecimal todayReturned;

    /**
     * 当前登录人今日损耗（loss SUM）。
     */
    private BigDecimal todayLoss;

}
