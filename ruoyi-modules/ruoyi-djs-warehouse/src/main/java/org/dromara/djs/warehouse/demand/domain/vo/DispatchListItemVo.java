package org.dromara.djs.warehouse.demand.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * mp 调度需求列表项出参（WMS-DEMAND-002）。
 *
 * <p>专供白条 {@code /white-bar/list} + 蔬菜 {@code /vegetable/list} 列表——mp 调度员按门店 + 日期
 * 分组展示。本 VO 是单条需求轻量视图，mp 端在前端按 {@code storeId} 分组成卡。</p>
 *
 * <p>白条卡用：门店名 / 需求量(头) / 处理时间 / 状态徽标 / 已指定猪只数 → 点进 pig-select。
 * 蔬菜卡用：门店名 / 需求量 / 单位 / 状态徽标 → 点进确认弹窗。</p>
 *
 * <p>JSON 序列化：{@code id} / {@code storeId} 走 Long → string 全局规则。</p>
 *
 * @author djs
 * @since WMS-DEMAND-002
 */
@Data
public class DispatchListItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 需求 ID（snowflake string）。 */
    private Long id;

    /** 需求单号（业务码）。 */
    private String demandNo;

    /** 需求日期。 */
    private LocalDate demandDate;

    /** 提单门店 ID（snowflake string，mp 端分组键）。 */
    private Long storeId;

    /** 门店名（service enrich，避免 mp 端二次查）。 */
    private String storeName;

    /** 产品名。 */
    private String productName;

    /** 产品规格快照；白条半扇/整扇识别不能只依赖产品名。 */
    private String productSpec;

    /** 业态：white_bar / vegetable / gift_box / other。 */
    private String productType;

    /** 需求量。 */
    private BigDecimal demandQuantity;

    /** 单位（kg / 头 / 盒）。 */
    private String productUnit;

    /**
     * 每一份白条订单折算的猪只头数：半扇=0.5，整扇=1。
     * <p>由后端依据产品名和规格快照结构化给出，客户端不再自行猜测中文名称。</p>
     */
    private BigDecimal whiteBarHeadFactor;

    /** 需求说明（如 "25 号之前每天 1 头猪送到矿业 / 背膘不要太厚"）。 */
    private String demandExplain;

    /** 状态：DRAFT/SUBMITTED/CONFIRMED/IN_PRODUCTION/PARTIAL_SHIPPED/COMPLETED/CANCELLED。 */
    private String demandStatus;

    /** 期望到货日（mp 卡「处理时间」展示用）。 */
    private LocalDate expectedArriveDate;

    /** 已指定猪只数（白条业态——service COUNT t_warehouse_demand_pig；非白条为 0）。 */
    private Integer assignedPigCount;
}
