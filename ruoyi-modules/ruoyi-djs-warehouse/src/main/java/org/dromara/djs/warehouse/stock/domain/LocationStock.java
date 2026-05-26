package org.dromara.djs.warehouse.stock.domain;

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
 * 库存明细实体（WMS-MD-001）。
 *
 * <p>对应表 {@code t_warehouse_location_stock}（V202605290900 建）：</p>
 * <ul>
 *   <li>3 维互斥关联：{@code productId} / {@code earNo} / {@code plotId} 三选一，V1 应用层校验</li>
 *   <li>{@code operatorId} 由 service insert 时通过 {@link org.dromara.common.satoken.utils.LoginHelper#getUserId()}
 *       注入（ADR-0007 强制；冗余存最后操作人便于追溯，独立于 {@code createBy}）</li>
 *   <li>库存写入入口：本 ticket admin 不暴露 add/edit；后续 WMS-DEMAND-001 / WMS-STOCK-001 D8-D11
 *       通过出入库流水触发更新本表</li>
 * </ul>
 *
 * @author djs
 * @since WMS-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_location_stock")
public class LocationStock extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 所在库位（FK → t_warehouse_location_info.id）。
     */
    private Long locationId;

    /**
     * 产品 ID（FK → t_warehouse_product_info.id，WMS-MD-002 D8 建；与 earNo/plotId 三选一）。
     */
    private Long productId;

    /**
     * 猪只耳号（白条入库按耳号关联；与 productId/plotId 三选一）。
     */
    private String earNo;

    /**
     * 地块 ID（蔬菜采摘入库按地块关联；与 productId/earNo 三选一）。
     */
    private Long plotId;

    /**
     * 产品名称（冗余字段，便于列表展示，免 JOIN）。
     */
    private String productName;

    /**
     * 当前库存数量。
     */
    private BigDecimal productStock;

    /**
     * 产品单位（kg / 头 / 箱 等）。
     */
    private String productUnit;

    /**
     * 是否完成（字典 {@code djs_yes_no}：1=是（已用完不显示）/ 0=否（进行中））。
     */
    private Integer isEnd;

    /**
     * 最新盘点时间（由 WMS-STOCK-001 D11 盘点流程更新）。
     */
    private Date latestCheckTime;

    /**
     * 盘点结果（字典 {@code djs_check_result}：1=正常 / 2=异常 / 3=计损）。
     */
    private Integer checkResult;

    /**
     * 最后操作人（FK → {@code sys_user.user_id}；ADR-0007）。
     */
    private Long operatorId;

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
