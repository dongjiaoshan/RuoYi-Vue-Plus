package org.dromara.djs.warehouse.shipment.returnpkg.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退货管理实体（{@code t_warehouse_return_product}，doc/11 §2.11 + WMS-SHIP-001 D10 决策 a 加 return_direction）。
 *
 * <p>V1 范围（Kevin 决策 a）：</p>
 * <ul>
 *   <li>方向 {@code store_to_warehouse} — 主路径，admin 确认时联动 stock_flow(return_in)</li>
 *   <li>方向 {@code customer_to_store} / {@code warehouse_to_supplier} — 仅录入占位，不联动 stock_flow</li>
 * </ul>
 *
 * @author djs
 * @since WMS-SHIP-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_return_product")
public class ReturnProduct extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 退货单号（业务码 RET+yyyyMMdd+seq4，inline 生成）。 */
    private String returnNo;

    /** 退货门店 FK → {@code t_md_store.id}（其他方向可空）。 */
    private Long storeId;

    /** 退货申请时间。 */
    private LocalDateTime applyTime;

    /** 产品 FK → {@code t_warehouse_product_info.id}。 */
    private Long productId;

    /** 产品名（冗余，便于列表展示）。 */
    private String productName;

    /** 退货重量。 */
    private BigDecimal returnWeight;

    /** 确认重量（admin 复核后）。 */
    private BigDecimal confirmWeight;

    /** 确认人 user_id → {@code sys_user.user_id}。 */
    private Long confirmUser;

    /** 确认时间。 */
    private LocalDateTime confirmTime;

    /** 是否已确认：1=是 / 0=否。 */
    private Integer isConfirm;

    /** 退货原因。 */
    private String returnReason;

    /** 退货方向字典 {@code djs_return_direction}：customer_to_store / store_to_warehouse / warehouse_to_supplier。 */
    private String returnDirection;

    /** 退货状态字典 {@code djs_return_status}：pending / confirmed / rejected。 */
    private String returnStatus;

    /** 退货凭证图 OSS IDs CSV（bizType={@code warehouse_return_proof}）。 */
    private String proofOssIds;

    /** 备注。 */
    private String remark;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 软删唯一辅助列。 */
    private Long delUnique;
}
