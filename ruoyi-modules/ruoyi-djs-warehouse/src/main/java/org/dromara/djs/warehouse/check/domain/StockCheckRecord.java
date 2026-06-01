package org.dromara.djs.warehouse.check.domain;

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
 * 盘点记录实体（WMS-STOCK-001）。
 *
 * <p>对应表 {@code t_warehouse_check_record}（V202606130920 重建）。同一盘点单的
 * header 行 + 各 line 行共用同一 {@code checkId} 业务码，用 {@code isHeader} 区分：</p>
 * <ul>
 *   <li>{@code isHeader=1} 盘点单头：承载 {@code checkStatus}（draft / in_progress / completed）与库位锁；
 *       {@code productId=0} 占位、{@code sysStock / checkStock / diffStock=0}</li>
 *   <li>{@code isHeader=0} 盘点明细 line：每个 (location_id, product_id) 一行实盘记录，
 *       {@code sysStock}（盘点时系统量）/ {@code checkStock}（实盘量）/ {@code diffStock = check - sys}</li>
 * </ul>
 *
 * <p>库位级业务锁：盘点单 header {@code checkStatus='in_progress'} 时，对应 {@code locationId} 被锁，
 * stock_flow 写入入口（{@code MatFlowServiceImpl} / {@code PigBurnRecordServiceImpl}）抛 ServiceException 拒绝。
 * admin 完成（completed）/ 取消（completed 不回写）后解锁。</p>
 *
 * <p>软删走 {@code delFlag} + {@code delUnique}（{@link org.dromara.djs.common.base.DjsBaseServiceImpl#softDelete}）。</p>
 *
 * @author djs
 * @since WMS-STOCK-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_check_record")
public class StockCheckRecord extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 盘点单业务码 {@code C{yyyyMMdd}{seq5}}（header + 各 line 共用）。
     */
    private String checkId;

    /**
     * 库位 ID（FK → {@code t_warehouse_location_info.id}；盘点按库位）。
     */
    private Long locationId;

    /**
     * 产品 ID（FK → {@code t_warehouse_product_info.id}；header 行写 0 占位）。
     */
    private Long productId;

    /**
     * 产品名称（冗余）。
     */
    private String productName;

    /**
     * 产品单位（冗余）。
     */
    private String productUnit;

    /**
     * 盘点时系统库存量。
     */
    private BigDecimal sysStock;

    /**
     * 实盘量。
     */
    private BigDecimal checkStock;

    /**
     * 差异 = {@code checkStock - sysStock}（&gt;0 盘盈 / &lt;0 盘亏）。
     */
    private BigDecimal diffStock;

    /**
     * 盘点结果类型（字典 {@code djs_check_result}：1=正常 / 2=异常 / 3=计损）。
     */
    private Integer checkResultType;

    /**
     * 差异原因。
     */
    private String diffReason;

    /**
     * 实盘录入人（FK → {@code sys_user.user_id}；header 行可空）。
     */
    private Long checkBy;

    /**
     * 盘点日期。
     */
    private Date checkDate;

    /**
     * 盘点单状态（字典 {@code djs_check_status}：draft / in_progress / completed）；仅 header 行有意义。
     */
    private String checkStatus;

    /**
     * 1=盘点单头（承载 status + 库位锁）/ 0=盘点明细 line。
     */
    private Integer isHeader;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

    /**
     * 软删唯一性辅助列。
     */
    private Long delUnique;

}
