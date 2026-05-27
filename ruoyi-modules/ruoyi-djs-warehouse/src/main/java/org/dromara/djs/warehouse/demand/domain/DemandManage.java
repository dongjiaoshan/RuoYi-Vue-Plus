package org.dromara.djs.warehouse.demand.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 需求管理主表实体（WMS-DEMAND-001）。
 *
 * <p>对应表 {@code t_warehouse_demand_manage}。4 业态共表：</p>
 * <ul>
 *   <li>{@code productType=white_bar} 白条 — 需配套 {@link DemandPig} 关联猪只</li>
 *   <li>{@code productType=vegetable} 蔬菜 — 走 {@code raw_material} + {@code material_qty}</li>
 *   <li>{@code productType=gift_box} 礼盒 — 内部使用，product_id 指向礼盒 SKU</li>
 *   <li>{@code productType=other} 其他</li>
 * </ul>
 *
 * <p>状态 7 态由 {@code DemandStateMachine} 推进；audit_history JSON 累计转移记录。</p>
 *
 * @author djs
 * @since WMS-DEMAND-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_demand_manage")
public class DemandManage extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（snowflake）。 */
    @TableId
    private Long id;

    /**
     * 需求单号（业务码，{@code BizCodeType.DEMAND_NO} 生成；
     * UNIQUE(tenant_id, demand_no, del_unique)）。
     */
    private String demandNo;

    /** 需求日期。 */
    private LocalDate demandDate;

    /** 提单门店，FK {@code t_md_store.id}。 */
    private Long storeId;

    /** 产品 ID，FK {@code t_warehouse_product_info.id}（snowflake，**不是**业务码 product_id VARCHAR）。 */
    private Long productId;

    /** 产品名（冗余，便于列表展示不再 JOIN）。 */
    private String productName;

    /** 产品规格。 */
    private String productSpec;

    /** 业态：white_bar / vegetable / gift_box / other（字典 {@code djs_demand_product_type}）。 */
    private String productType;

    /** 需求量。 */
    private BigDecimal demandQuantity;

    /** 单位（kg / 头 / 盒）。 */
    private String productUnit;

    /** 原材料描述，例 "需要 5 头猪"。 */
    private String rawMaterial;

    /** 原材料计算量。 */
    private BigDecimal materialQty;

    /** 需求备注。 */
    private String demandRemark;

    /** 需求说明（如 "25 号之前每天 1 头猪送到矿业"）。 */
    private String demandExplain;

    /** 状态：DRAFT/SUBMITTED/CONFIRMED/IN_PRODUCTION/PARTIAL_SHIPPED/COMPLETED/CANCELLED。 */
    private String demandStatus;

    /** 确认人 user_id，FK {@code sys_user.user_id}。 */
    private Long demandConfirmer;

    /** 确认时间。 */
    private LocalDateTime confirmerTime;

    /** 期望到货日。 */
    private LocalDate expectedArriveDate;

    /** 累计已发货量（CROSS-FLOW-003 D14 listener 原子 += 更新）。 */
    private BigDecimal shippedCount;

    /** 累计已确认量。 */
    private BigDecimal confirmedCount;

    /**
     * 状态历史 JSON 串，列表元素为
     * {@code {from,to,event,operator,operatorName,ts,remark}}；V2 切 Flowable 时可拆独立表。
     */
    private String auditHistory;

    /** 备注。 */
    private String remark;

    /** 软删标记（'0' 未删 / '1' 已删）。 */
    @TableLogic
    private String delFlag;

    /** 软删唯一性辅助列；{@code DjsMetaObjectHandler} 软删时写入 id。 */
    private Long delUnique;
}
