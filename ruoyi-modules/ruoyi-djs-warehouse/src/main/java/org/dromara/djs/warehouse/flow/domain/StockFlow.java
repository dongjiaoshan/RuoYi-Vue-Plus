package org.dromara.djs.warehouse.flow.domain;

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
 * 出入库流水实体（WMS-MAT-001 / WMS-PIG-001 minimal 占位）。
 *
 * <p>对应表 {@code t_warehouse_stock_flow}（D7 V202605290910 WMS-MD-001 已建）。本 ticket
 * 在 WMS-PIG-001 燎毛工序提交时 INSERT 一条出库流水（{@code flow_type=slaughter_burn / inout_type=OT}），
 * 字段对齐 DDL；D9 WMS-MAT-001 落地后会补全 controller/service/mapper 完整 CRUD，
 * 本 ticket 不动 DDL、不开 admin 写入入口。</p>
 *
 * <p>{@code inoutType} DDL 是 {@code CHAR(3)} 取值 IN / OT。</p>
 *
 * @author djs
 * @since WMS-PIG-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_stock_flow")
public class StockFlow extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 流水单号（业务码 {@code F+yyyyMMdd+ioCode2+seq4}，{@link org.dromara.djs.common.encoder.BizCodeType#STOCK_FLOW_NO} 生成）。
     */
    private String flowNo;

    /**
     * 流水时间。
     */
    private Date flowDate;

    /**
     * 产品 ID（FK → {@code t_warehouse_product_info.id}，WMS-MD-002 D8 建；
     * 本 ticket 白条出库无 product 主数据，先冗余写 0，下游 WMS-MAT-001 再规整）。
     */
    private Long productId;

    /**
     * 库位 ID（白条所在位置）。
     */
    private Long warehouseId;

    /**
     * 出入库方向：IN=入库 / OT=出库（CHAR(3)）。
     */
    private String inoutType;

    /**
     * 流水类型字典（本 ticket 写入 {@code slaughter_burn}）。
     */
    private String flowType;

    /**
     * 入库子类型（本 ticket 不用）。
     */
    private String stockInType;

    /**
     * 出库子类型（本 ticket 不用）。
     */
    private String stockOutType;

    /**
     * 出库去向（本 ticket 不用）。
     */
    private String stockOutDest;

    /**
     * 变更数量（出库为正，符号由 {@code inout_type} 区分；本 ticket 写 {@code burnWeight}）。
     */
    private BigDecimal changeNum;

    /**
     * 变更件数 / 包数（本 ticket 同 changeNum 写一份）。
     */
    private BigDecimal changeQuantity;

    /**
     * 供应商 ID（本 ticket 不用）。
     */
    private Long supplierId;

    /**
     * 猪只耳号（白条流水按耳号关联）。
     */
    private String earNo;

    /**
     * 地块 ID（蔬菜流水用，本 ticket 不用）。
     */
    private Long plotId;

    /**
     * 操作人。
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
