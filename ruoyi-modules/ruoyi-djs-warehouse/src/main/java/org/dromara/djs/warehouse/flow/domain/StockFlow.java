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
 * 出入库流水实体（{@code t_warehouse_stock_flow}）。
 *
 * <p>DDL 建表来源：D7 V202605290910 WMS-MD-001；本 ticket（D9 WMS-MAT-001）扩字段 {@code proofOssIds}
 * （凭证图 OSS IDs CSV）+ 全字段开放给 mp 物资领用 / 退回 / 损耗与 admin 流水查询。</p>
 *
 * <p>三种写入入口：</p>
 * <ol>
 *   <li>{@link org.dromara.djs.warehouse.burn.service.impl.PigBurnRecordServiceImpl#submitBurnRecord} ——
 *       燎毛出库（{@code flow_type=slaughter_burn / inout_type=OT}，D8）</li>
 *   <li>{@code MatFlowServiceImpl#pick/returnBack/loss} —— 物资领用 / 退回 / 损耗
 *       （{@code flow_type=pick_out|return_in|loss}，本 ticket）</li>
 *   <li>D9 WMS-PIG-002 / WMS-VEG-001 / D11 WMS-FLOW-001 / D11 WMS-STOCK-001 续接其他业务事件</li>
 * </ol>
 *
 * <p>{@code inoutType} DDL 是 {@code CHAR(3)} 取值 {@code IN}/{@code OT}（不是 1/2）。</p>
 *
 * @author djs
 * @since WMS-PIG-001（D9 WMS-MAT-001 扩 proofOssIds）
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
     * 库位 ID（实为 location FK → {@code t_warehouse_location_info.id}；DDL 字段名 {@code warehouse_id} 是 D7 命名遗留，
     * 详 doc/11 §2.3 + D9 _open-issues #2 决策 b，物理列保留、含义以注释为准）。
     */
    private Long warehouseId;

    /**
     * 关联需求单 ID（仅 {@code flow_type=ship_out} 流水写入；
     * D10 WMS-SHIP-001 service 写入，D14 CROSS-FLOW-003 listener 按本字段聚合 {@code shipped_count}）。
     */
    private Long demandId;

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
     * 白条 ID（= {@code t_warehouse_bar_info.id}）。仅分割产出 {@code cut_out_in} 流水写值，
     * 分割「剩余可分割重量 / 超量校验」按此聚合——外购白条无耳号（{@code ear_no=NULL}）也能稳定关联。
     */
    private Long whiteBarId;

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
     * 凭证图 OSS IDs CSV（{@code bizType=warehouse_mat_pick}；可选，仅 mp 物资 / 燎毛流水使用）。
     */
    private String proofOssIds;

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
