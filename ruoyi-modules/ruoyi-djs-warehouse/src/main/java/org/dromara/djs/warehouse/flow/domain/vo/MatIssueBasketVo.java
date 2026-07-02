package org.dromara.djs.warehouse.flow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 物资领用「按源篮子」VO（「按源手选」领用，对齐客户最新原型「仓库&gt;分拣发货&gt;物资领用」）。
 *
 * <p>领用粒度改为「按源手选」：猪肉(分割)按【耳号】、蔬菜(自产)按【地块】，用户在源篮子列表里手选某一篮
 * 领 / 退 / 损（不再 FIFO 系统选篮）。一行 = 一个 {@code location_stock} 篮子：</p>
 * <ul>
 *   <li>猪肉篮（{@code issuePorkBatches}）：{@code ear_no} 非空、{@code batchCode = ear_no}（耳号）；</li>
 *   <li>自产果蔬篮（{@code issueVegBatches}）：{@code plot_id} 非空、{@code batchCode = 地块编号}
 *       （LEFT JOIN {@code t_plant_plot_info.plot_code}）。</li>
 * </ul>
 *
 * <p>两查询都只返 {@code product_stock > 0} 的篮（库存用完的篮自动不出现 → 满足「用完不可选」）。
 * 字段与 mp 已声明的 {@code MatVegBatchVo} 同构。</p>
 *
 * <p>跨层契约：{@code batchId} / {@code productId} / {@code locationId} 全部 String（snowflake 19 位防
 * jackson Long 截断）；{@code currentStock} = 该篮真实余量；今日三量 best-effort（见各字段注释）。</p>
 *
 * @author djs
 * @since FIX-WMS-MATISSUE-BYSOURCE
 */
@Data
public class MatIssueBasketVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 篮子/卡 ID（String 防截断；行内领/退/损提交时作 {@code batchId} 回传）。
     *
     * <p>猪肉篮 = 该篮 {@code location_stock.id}；自产果蔬地块卡（已按地块聚合）= 该地块 FIFO 首篮
     * {@code MIN(location_stock.id)}，service 据该篮 {@code plot_id} 识别为地块卡 → 按 {@code (productId, plotId)}
     * 跨库位整地块领/退/损（一个地块在小程序里就是一张卡，多库位是线下实际、mp 不体现）。</p>
     */
    private String batchId;

    /** 业务码（卡片次标题）：猪肉篮 = 耳号 {@code ear_no}；自产果蔬地块卡 = 地块编号 {@code plot_code}。 */
    private String batchCode;

    /** 产品 ID（= 该篮/地块 {@code product_id}，String；领用 BO 回传，与 productId 入参一致）。 */
    private String productId;

    /** 产品名称（卡片主标题，来自篮 {@code product_name} 或 product 主数据）。 */
    private String productName;

    /** 单位（kg 等）。 */
    private String productUnit;

    /**
     * 当前库存：猪肉篮 = 该篮余量 {@code product_stock}；自产果蔬地块卡 = 该地块各库位篮
     * {@code SUM(product_stock)}（地块级总量；只返 &gt; 0 的地块）。
     */
    private BigDecimal currentStock;

    /**
     * 今日已领：今天 {@code product_inhouse} SUM(product_weight) WHERE {@code product_id + ear_no/plot_id}
     * （inhouse 带 ear_no/plot_id 源标签）。自产果蔬地块卡按 {@code plot_id} 聚合 = 地块级总量。
     */
    private BigDecimal todayPicked;

    /**
     * 今日已退：猪肉篮恒 0（{@code stock_flow} 不细分到耳号维度）；自产果蔬地块卡 = 今天 {@code stock_flow}
     * (return_in) SUM(change_quantity) WHERE {@code plot_id}（地块级总量）。
     */
    private BigDecimal todayReturned;

    /**
     * 今日损耗：猪肉篮恒 0；自产果蔬地块卡 = 今天 {@code stock_flow}(loss) SUM(change_quantity)
     * WHERE {@code plot_id}（地块级总量）。
     */
    private BigDecimal todayLoss;

    /**
     * 今日饲喂（仅自产果蔬地块卡）：今天 {@code stock_flow}(feed_out) SUM(change_quantity) WHERE {@code plot_id}
     * （地块级总量）；猪肉/白条篮无饲喂，恒 0/null。
     */
    private BigDecimal todayFeed;

    /**
     * 所属库位 ID（String；领用 BO 回传作 locationId）：猪肉篮 = 该篮 {@code location_id}；自产果蔬地块卡
     * = 该地块 FIFO 首篮库位（service 走地块 FIFO 跨库位扣减，此 locationId 仅 UX 占位、不限扣减范围）。
     */
    private String locationId;

}
