package org.dromara.djs.warehouse.flow.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 物资领用「白条批次卡」VO（录入形态②·白条库 / mp issueWhiteBarBatches）。
 *
 * <p>数据源 = {@code t_warehouse_bar_info WHERE status='in_stock'}（一行 = 一条实物白条整只），
 * 不走 location_stock（白条 SKU 无库存行）。每条在库白条作为一张可领批次卡，mp 行内「白条领用」
 * 提交时把 {@code batchId} / {@code productId} 回传给领用 BO。</p>
 *
 * <p>字段镜像前端契约 {@code miniapp/src/api/warehouse/mat.ts → MatWhiteBarBatchVo}：
 * batchId / productId / locationId 全部 String（snowflake 19 位防 jackson Long 截断）；
 * batchStatus 取值 {@code 'pickable' | 'picked'}（本端点只返 in_stock → 恒 {@code pickable}）。</p>
 *
 * @author djs
 * @since WMS-MAT-001-WHITEBAR
 */
@Data
public class MatWhiteBarBatchVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 批次 ID（白条主键 id，String 防截断；行内领用提交时作 batchId 回传）。 */
    private String batchId;

    /** 批次业务码 / 耳标号（chip 文案：bar_id 业务码 BAR+yyMMdd+4，为空回退 ear_no）。 */
    private String batchCode;

    /** 产品 ID（白条·整只 canonical SKU id，String；bar_info 无 product_id FK，由本端点回填给领用 BO）。 */
    private String productId;

    /** 产品名称（白条·整只）。 */
    private String productName;

    /** 单位（头；白条按头计数）。 */
    private String productUnit;

    /** 入库时间（in_time 格式化 {@code MM-dd HH:mm}；为空 → ""）。 */
    private String inboundTime;

    /** 指定门店名称（在库白条尚未绑定门店，领用时才指定门店 → 恒 null）。 */
    private String storeName;

    /** 预冷时长（按 in_time 实时计算 {@code now - in_time}，文案 {@code {h}小时{m}分钟}；in_time 为空 → ""）。 */
    private String precoolDuration;

    /** 入库重量 kg（in_weight）。 */
    private BigDecimal inboundWeight;

    /** 批次状态：{@code pickable}（可领，本端点只返 in_stock → 恒 pickable）/ {@code picked}（已领，已领态 chip 灰禁用）。 */
    private String batchStatus;

    /** 所属库位 ID（白条 SKU 无库存行 / 库位绑定 → 恒 null）。 */
    private String locationId;
}
