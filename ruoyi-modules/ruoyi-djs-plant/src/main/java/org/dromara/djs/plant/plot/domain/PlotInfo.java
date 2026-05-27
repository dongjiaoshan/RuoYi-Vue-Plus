package org.dromara.djs.plant.plot.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;
import org.dromara.djs.common.base.DjsBaseServiceImpl;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * 地块信息实体（PLT-MD-001）。
 *
 * <p>对应表 {@code t_plant_plot_info}（V202606050900）：</p>
 * <ul>
 *   <li>{@code zone_id} FK → {@code t_plant_plot_zone.id}（一对多，方案 A 固化）</li>
 *   <li>下游 {@code t_plant_plant_plan} / {@code t_plant_farm_records} 通过 {@code plot_id} 关联</li>
 *   <li>删除前校验：地块若有种植计划 / 农事记录 / 采摘数据，应用层拒绝（D8 时三表 0 行，service 写 stub）</li>
 *   <li>OSS 字段：{@code plotImagePreview}（缩略单张 ossId）+ {@code plotImageUrl}（原图多张 ossIds 逗号分隔）</li>
 * </ul>
 *
 * <p>软删走 {@link DjsBaseServiceImpl#softDelete}。</p>
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_plant_plot_info")
public class PlotInfo extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键（雪花）。
     */
    @TableId
    private Long id;

    /**
     * 地块业务码（用户手填；UNIQUE(tenant_id, plot_code, del_unique)）。
     */
    private String plotCode;

    /**
     * 缩略图 OSS objectName（单张）。
     */
    private String plotImagePreview;

    /**
     * 原图 OSS objectNames 逗号分隔（多张）。
     */
    private String plotImageUrl;

    /**
     * 所属片区 FK → {@code t_plant_plot_zone.id}。
     */
    private Long zoneId;

    /**
     * 地块类型（字典 {@code djs_plot_type}：open / shed / greenhouse）。
     */
    private String plotType;

    /**
     * 地块名称。
     */
    private String plotName;

    /**
     * 地块状态（字典 {@code djs_plot_status}：1=空闲 / 2=种植 / 3=采摘）。
     */
    private Integer plotStatus;

    /**
     * 是否租赁（字典 {@code djs_yes_no}：1=是 / 0=否）。
     */
    private Integer isLease;

    /**
     * 地块业务备注（区别于系统审计 remark）。
     */
    private String plotRemark;

    /**
     * 面积（亩）。
     */
    private BigDecimal plotArea;

    /**
     * 位置文字描述。
     */
    private String plotLocationDesc;

    /**
     * 经度。
     */
    private BigDecimal plotLocationX;

    /**
     * 纬度。
     */
    private BigDecimal plotLocationY;

    /**
     * 土壤类型（字典 {@code djs_soil_type}）。
     */
    private String soilType;

    /**
     * 土壤肥力（字典 {@code djs_soil_fertility}）。
     */
    private String soilFertility;

    /**
     * 土壤 PH 值。
     */
    private BigDecimal soilPh;

    /**
     * 地势（字典 {@code djs_terrain_condition}）。
     */
    private String terrainCondition;

    /**
     * 光照（字典 {@code djs_light_condition}）。
     */
    private String lightCondition;

    /**
     * 排水（字典 {@code djs_drain_condition}）。
     */
    private String drainCondition;

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
