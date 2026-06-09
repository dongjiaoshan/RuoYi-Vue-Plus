package org.dromara.djs.warehouse.pigbuy.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 外购猪只到货登记实体（FIX-WMS-MP-PIGBUY-001）。
 *
 * <p>对应表 {@code t_warehouse_pig_purchase}（V202606200900）。外购活猪 / 白条独立登记到货入库，
 * 作为燎毛 / 分割的另一来源（与自养出栏并列）；按口径 D-FIX-7 #4.3 <b>不进养殖 BRD 引种</b>
 * （引种=繁殖群）。本卡范围：建表 + 到货登记 + 外购待处理列表，不动 burn / purchase 共享模块。</p>
 *
 * <ul>
 *   <li>{@code purchaseNo}：业务码 {@code PBUY+YYMMDD+4 位序号}，本表幂等键，UNIQUE (tenant_id, purchase_no, del_unique)</li>
 *   <li>{@code sourceType}：字典 {@code djs_pig_source}（{@code live} 活猪 / {@code white_bar} 白条）</li>
 *   <li>{@code purchaseStatus}：字典 {@code djs_pig_purchase_status}（{@code pending} 待处理 / {@code done} 已处理）；
 *       本卡登记即 {@code pending}，进燎毛 / 分割流后置 {@code done} 留 follow-up</li>
 *   <li>{@code operatorId}：mp 登记人（{@link org.dromara.common.satoken.utils.LoginHelper#getUserId()} 注入，ADR-0007）</li>
 *   <li>{@code proofOssIds}：到货凭证图 OSS IDs CSV</li>
 * </ul>
 *
 * @author djs
 * @since FIX-WMS-MP-PIGBUY-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_pig_purchase")
public class PigPurchase extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键。
     */
    @TableId
    private Long id;

    /**
     * 业务码 {@code PBUY+YYMMDD+4}（本表幂等键）。
     */
    private String purchaseNo;

    /**
     * 来源类型（字典 {@code djs_pig_source}：live / white_bar）。
     */
    private String sourceType;

    /**
     * 到货数量（头 / 条）。
     */
    private Integer quantity;

    /**
     * 到货重量 kg。
     */
    private BigDecimal arriveWeight;

    /**
     * 供应商名称（自由文本）。
     */
    private String supplierName;

    /**
     * 到货时间。
     */
    private Date arriveTime;

    /**
     * 处理状态（字典 {@code djs_pig_purchase_status}：pending / done）。
     */
    private String purchaseStatus;

    /**
     * 登记人（FK → {@code sys_user.user_id}）。
     */
    private Long operatorId;

    /**
     * 到货凭证图 OSS IDs CSV。
     */
    private String proofOssIds;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;

}
