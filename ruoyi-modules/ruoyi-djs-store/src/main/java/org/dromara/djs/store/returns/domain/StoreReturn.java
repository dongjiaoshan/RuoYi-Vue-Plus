package org.dromara.djs.store.returns.domain;

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
 * 门店退回管理实体（{@code t_store_return}，doc/11 §3.9 + STR-RETURN-001 决策 a 门店域薄实现）。
 *
 * <p>表由 SYS-INIT-001 建 stub（含全部业务列 + 6 公共字段 + tenant_id + del_unique + uk_return_no），
 * 本实体仅对接现有 stub，不重建表。</p>
 *
 * <h3>与 warehouse 退货（{@code t_warehouse_return_product}）的差异</h3>
 * <ul>
 *   <li>多 {@code memberId}（顾客→门店会员退回）+ {@code traceCode}（追溯码字符串）两列</li>
 *   <li>挂门店板块（菜单段 10350-10379），主场景 {@code customer_to_store}</li>
 *   <li>V1 不做库存联动 / 不做状态机（仓库侧退货由 WMS-SHIP-001 负责，避免双写库存）</li>
 * </ul>
 *
 * @author djs
 * @since STR-RETURN-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_store_return")
public class StoreReturn extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（MP 雪花；stub 列虽为 auto_increment，entity 不带 IdType.AUTO，显式赋雪花值）。 */
    @TableId
    private Long id;

    /** 退回单号（业务码 RET+yyyyMMdd+seq4，走 BizCodeType.RETURN_NO）。 */
    private String returnNo;

    /** 退回方向字典 {@code djs_return_direction}：customer_to_store / store_to_warehouse / warehouse_to_supplier。 */
    private String returnDirection;

    /** 退回门店 FK → {@code t_md_store.id}（customer_to_store 方向必填，其余可空）。 */
    private Long storeId;

    /** 产品 FK → {@code t_warehouse_product_info.id}。 */
    private Long productId;

    /** 退回数量。 */
    private BigDecimal returnQuantity;

    /** 退回原因（自由文本，对齐 t_warehouse_return_product.return_reason 范式）。 */
    private String returnReason;

    /** 已贴追溯码字符串，V1 仅存值无 FK 无校验（t_trace_code D14 才建）。 */
    private String traceCode;

    /** 退回日期。 */
    private LocalDateTime returnDate;

    /** 顾客退回会员 FK → {@code t_store_member.id}，V1 仅存值不强校验。 */
    private Long memberId;

    /** 经手人 user_id → {@code sys_user.user_id}（LoginHelper.getUserId()）。 */
    private Long operatorId;

    /** 备注。 */
    private String remark;

    /** 软删标记。 */
    @TableLogic
    private String delFlag;

    /** 软删唯一辅助列。 */
    private Long delUnique;
}
