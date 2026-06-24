package org.dromara.djs.warehouse.loss.domain;

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
 * 统一损耗流水台账实体（WMS-LOSS-001，仓库-admin 6/24 行58）。
 *
 * <p>对应表 {@code t_warehouse_loss_flow}（V202607251000）。系统所有「会产生损耗」的业务环节
 * （物资领用录入损耗 / 毛菜处理损耗 / 运输损耗 / 盘点计损 / 白条预冷+分割损耗 / 生产损耗）
 * 在原有逻辑（stock_flow / 各表 loss_weight 列）之上，统一调
 * {@link org.dromara.djs.warehouse.loss.service.ILossFlowService#record} 双写一条本表明细，
 * 作为「损耗总览 / 库存查询详情损耗 tab / 库存总览损耗列」的统一数据源。</p>
 *
 * @author djs
 * @since WMS-LOSS-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_warehouse_loss_flow")
public class LossFlow extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /**
     * 损耗发生时间。
     */
    private Date lossDate;

    /**
     * 产品 ID（FK → {@code t_warehouse_product_info.id}；crop/plot 级损耗如毛菜处理/运输可空）。
     */
    private Long productId;

    /**
     * 产品编码（冗余快照 = {@code ProductInfo.productId} 业务码，行58 核心字段）。
     */
    private String productCode;

    /**
     * 产品名称（冗余快照）。
     */
    private String productName;

    /**
     * 单位（冗余快照）。
     */
    private String productUnit;

    /**
     * 业态归属（冗余，字典 {@code djs_belong_type}；便于按品类聚合/过滤）。
     */
    private String belongType;

    /**
     * 损耗类型（字典 {@code djs_loss_type}）。
     */
    private String lossType;

    /**
     * 损耗量。
     */
    private BigDecimal lossWeight;

    /**
     * 损耗位置（库位 FK → {@code t_warehouse_location_info.id}）。
     */
    private Long locationId;

    /**
     * 损耗位置名称（冗余快照）。
     */
    private String locationName;

    /**
     * 操作人（系统自动算的损耗如生产损耗为空）。
     */
    private Long operatorId;

    /**
     * 来源业务（veg_handle/transport/mat/cut/check/production）。
     */
    private String sourceBizType;

    /**
     * 来源记录 ID（追溯回指源单）。
     */
    private Long sourceBizId;

    /**
     * 关联 {@code stock_flow.id}（若损耗同时是一条出库流水）。
     */
    private Long sourceFlowId;

    /**
     * 猪只耳号 / 白条号（白条损耗追溯标签）。
     */
    private String earNo;

    /**
     * 地块 ID（果蔬损耗追溯标签）。
     */
    private Long plotId;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 软删标记（'0' 未删 / '1' 已删）。
     */
    @TableLogic
    private String delFlag;

}
