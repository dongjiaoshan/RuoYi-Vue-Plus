package org.dromara.djs.warehouse.pack.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 发货产品生产记录 — 产品维度聚合行 VO。
 *
 * <p>按「生产日期 + 产品(product_id)」分组聚合：同一日期同一产品的 N 件生产记录合并成一行。
 * 主列表只展示概览（生产日期 / 产品名称 / 产品品类 / 生产量 / 件数），逐件明细经「查看」
 * 下钻 {@link org.dromara.djs.warehouse.pack.controller.ProductProductionController#items}
 * 子页查看。范式同 {@link org.dromara.djs.warehouse.demand.domain.vo.DemandGroupVo}。</p>
 *
 * <p>{@code productId} 序列化为 string 避免雪花 ID JS 精度截断；前端「查看」下钻携
 * {@code produceDate + productId} 锁定生产批次。</p>
 *
 * @author djs
 * @since WMS-PACK-001
 */
@Data
public class ProductProductionGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 生产日期（DATE(produce_date)）。 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date produceDate;

    /** 产品 ID（string 输出避雪花精度）。 */
    private Long productId;

    /** 产品名称（组内取任一，冗余字段同组通常一致）。 */
    private String productName;

    /** 单位（组内取任一）。 */
    private String productUnit;

    /** 规格（组内取任一）。 */
    private String productSpec;

    /** 产品品类（字典 {@code djs_belong_type}；JOIN product_info 取 belong_type，前端 dict-tag 渲染）。 */
    private String belongType;

    /** 产品类型（字典 {@code djs_product_type}：1=自产/2=外购，已废弃 3 礼盒；保留兼容，主列表不展示）。 */
    private Integer productType;

    /** 该产品生产日的有效需求总量（取消/删除需求不计）。 */
    private BigDecimal demandQty;

    /**
     * 需求满足率（客户验收口径）：{@code demandQty / produceQty * 100%}。
     */
    private BigDecimal fulfillmentRate;

    /**
     * 生产量（按产品自身计量单位）：
     * <ul>
     *   <li>kg / 公斤 计量 → 当日 {@code SUM(product_weight)}（真实产出重量）；</li>
     *   <li>份 / 盒 / 枚等计数单位 → {@code COUNT(*)}（一次打包/出库确认 = 一份，与子页「产品明细」条数一致，
     *       避免份计量产品按重量算被取整后显 0）。</li>
     * </ul>
     * 与 {@link #demandQty}（同样按该单位下单）同量纲，{@link #fulfillmentRate} 才成立。
     */
    private BigDecimal produceQty;

    /** 件数（组内行数 COUNT(*)）。 */
    private Integer itemCount;

    /**
     * 原材料消耗量：该组（同产品同生产日）所有生产记录的 {@code SUM(material_consume)}。
     *
     * <p>每条打包记录的 {@code material_consume} = 本次打包消耗的来源原材料重量；组内求和即该产品当日
     * 累计原材料消耗。无配料（{@code material_id} 空）的记录 material_consume 为 NULL，SUM 跳过 → 该组可能为 0。</p>
     */
    private BigDecimal materialConsume;

    /**
     * 原材料名称：组内 {@code material_id} 对应 {@code product_info.product_name}（同组通常同一原料，取任一）。
     *
     * <p>SQL 经 {@code material_id} LEFT JOIN product_info 取 {@code MAX(product_name)} 兜底；无配料则空，前端展示 -。</p>
     */
    private String materialName;

    /**
     * 原材料单位：组内 {@code material_id} 对应 {@code product_info.product_unit}（同组通常同一原料，取任一）。
     *
     * <p>SQL 经 {@code material_id} LEFT JOIN product_info 取 {@code MAX(unit)} 兜底；无配料则空。</p>
     */
    private String materialUnit;

    /**
     * 需求门店数：该产品当前有未发货需求的门店去重家数。
     *
     * <p>口径对齐打包台「门店(N份)」标签（{@code DemandManageMapper.selectStoreDemandCopies}）：
     * 同 product_id 下，非取消单（{@code demand_status <> 'CANCELLED'}）、有门店，且该门店剩余
     * 需求量 {@code SUM(demand_quantity - shipped_count) > 0} 的去重门店家数。无需求则 0。</p>
     */
    private Integer storeDemandCount;

    /**
     * 损坏量：该组（同产品同生产日）已标损坏的件数 {@code SUM(is_damaged)}（DENGBO-DAMAGE-001）。
     *
     * <p>前端 row50「件数」后展示，&gt;0 红色提示。无标损则 0。</p>
     */
    private Integer damageCount;
}
