package org.dromara.djs.warehouse.vegreceive.domain;

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
 * 果蔬月台收货记录实体（FIX-WMS-VEGRECEIVE-001）。
 *
 * <p>对应表 {@code t_warehouse_veg_receive}（V202606200900 建）。记录"果蔬月台收货入库"动作，两类：</p>
 * <ul>
 *   <li>{@code receiveType=1} 自产：把上游毛菜处理"发往月台"（{@code vegetable_handle.send_platform_weight}）
 *       的果蔬入到保鲜室 {@code location_stock}（按 {@code plotId} 维度，库存三维互斥之一）</li>
 *   <li>{@code receiveType=2} 外购：外购果蔬产品验收入库（按 {@code productId} 维度 + {@code supplierId}）</li>
 * </ul>
 *
 * <h3>自产"待入库量"闭环（不造假）</h3>
 * <p>待入库 = {@code SUM(vegetable_handle.send_platform_weight)} − {@code SUM(本表 receiveType=1 已入库 weight)}，
 * 按 crop / plot 聚合。{@link org.dromara.djs.warehouse.vegreceive.mapper.VegReceiveMapper} 的聚合查询实现。</p>
 *
 * <h3>跨表事务</h3>
 * <p>由 {@link org.dromara.djs.warehouse.vegreceive.service.impl.VegReceiveServiceImpl} 维护：
 * 本表 INSERT + {@code location_stock} UPSERT（行锁增量 / 不存在 INSERT）+ {@code stock_flow} INSERT
 * （{@code flow_type=veg_receive_in} 自产 / {@code veg_purchase_in} 外购，{@code inout_type=IN}）。</p>
 *
 * @author djs
 * @since FIX-WMS-VEGRECEIVE-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_veg_receive")
public class VegReceive extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 收货流水号（冗余 {@code stock_flow.flow_no}，便于 mp 回显）。
     */
    private String receiveNo;

    /**
     * 收货类型 {@code djs_veg_receive_type}：1=自产 / 2=外购。
     */
    private Integer receiveType;

    /**
     * 作物 ID（自产 FK → {@code t_plant_crop_info.id}）/ 外购产品 ID（FK → {@code t_warehouse_product_info.id}）。
     */
    private Long cropId;

    /**
     * 作物 / 产品名称（冗余，免 JOIN）。
     */
    private String cropName;

    /**
     * 地块 ID（仅自产 FK → {@code t_plant_plot_info.id}；外购为 NULL）。
     */
    private Long plotId;

    /**
     * 地块编号（冗余，仅自产）。
     */
    private String plotCode;

    /**
     * 外购对应产品 ID（FK → {@code t_warehouse_product_info.id}；自产为 NULL，库存按 plotId 维度）。
     */
    private Long productId;

    /**
     * 本次入库重量(kg)。
     */
    private BigDecimal weight;

    /**
     * 损耗重量(kg)：仅自产用。地块标记「入库完成」(is_finish=1) 时，把当前剩余待入库重量
     * 结算为损耗写到该完成行；其余收货行恒 0。
     */
    private BigDecimal lossWeight;

    /**
     * 供应商 ID（仅外购 FK → {@code t_md_supplier.id}）。
     */
    private Long supplierId;

    /**
     * 供应商名称（冗余，仅外购）。
     */
    private String supplierName;

    /**
     * 入库库位 ID（FK → {@code t_warehouse_location_info.id}；保鲜室）。
     */
    private Long locationId;

    /**
     * 入库库位名称（冗余）。
     */
    private String locationName;

    /**
     * 是否入库完成 {@code djs_yes_no}：1=是 / 2=否（仅自产用，标记该地块/作物入库结束）。
     */
    private Integer isFinish;

    /**
     * 收货状态 {@code djs_veg_receive_status}：pending / processing / done。
     */
    private String receiveStatus;

    /**
     * 入库人（FK → {@code sys_user.user_id}；ADR-0007 冗余最后操作人，独立于 createBy）。
     */
    private Long operatorId;

    /**
     * 收货入库时间。
     */
    private Date receiveTime;

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
