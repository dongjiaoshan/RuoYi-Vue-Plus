package org.dromara.djs.plant.plot.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.djs.plant.plot.domain.PlotInfo;

import java.math.BigDecimal;

/**
 * 地块入参 BO（PLT-MD-001）。
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PlotInfo.class, reverseConvertGenerate = false)
public class PlotInfoBo extends BaseEntity {

    /** 地块 ID（编辑时必填）。 */
    private Long id;

    /** 业务码（新增时必填，编辑时后端强制锁回旧值）。 */
    @NotBlank(message = "{plant.plot.code.required}", groups = OnCreate.class)
    @Size(max = 32, message = "{plant.plot.code.size}")
    private String plotCode;

    /** 缩略图 OSS objectName。 */
    @Size(max = 512, message = "{plant.plot.image.size}")
    private String plotImagePreview;

    /** 原图 OSS objectNames 逗号分隔。 */
    @Size(max = 2048, message = "{plant.plot.image.size}")
    private String plotImageUrl;

    /** 所属片区。 */
    @NotNull(message = "{plant.plot.zone_required}")
    private Long zoneId;

    /** 地块类型（字典 djs_plot_type）。 */
    @Size(max = 32, message = "{plant.plot.type.size}")
    private String plotType;

    /** 地块名称。 */
    @NotBlank(message = "{plant.plot.name.required}")
    @Size(max = 64, message = "{plant.plot.name.size}")
    private String plotName;

    /** 状态（字典 djs_plot_status）。 */
    private Integer plotStatus;

    /** 是否租赁（字典 djs_yes_no：1=是 / 0=否）。 */
    private Integer isLease;

    /** 地块业务备注。 */
    @Size(max = 500, message = "{plant.plot.remark.size}")
    private String plotRemark;

    /** 面积（亩）。 */
    @NotNull(message = "{plant.plot.area_required}")
    @PositiveOrZero(message = "{plant.plot.area.positive}")
    private BigDecimal plotArea;

    /** 位置文字描述。 */
    @Size(max = 500, message = "{plant.plot.location.size}")
    private String plotLocationDesc;

    /** 经度（-180 ~ 180）。 */
    @DecimalMin(value = "-180", message = "{plant.plot.longitude.range}")
    @DecimalMax(value = "180", message = "{plant.plot.longitude.range}")
    private BigDecimal plotLocationX;

    /** 纬度（-90 ~ 90）。 */
    @DecimalMin(value = "-90", message = "{plant.plot.latitude.range}")
    @DecimalMax(value = "90", message = "{plant.plot.latitude.range}")
    private BigDecimal plotLocationY;

    /** 土壤类型（字典 djs_soil_type）。 */
    @Size(max = 32, message = "{plant.plot.soilType.size}")
    private String soilType;

    /** 土壤肥力（字典 djs_soil_fertility）。 */
    @Size(max = 32, message = "{plant.plot.soilFertility.size}")
    private String soilFertility;

    /** 土壤 PH 值（0-14）。 */
    @DecimalMin(value = "0", message = "{plant.plot.soilPh.range}")
    @DecimalMax(value = "14", message = "{plant.plot.soilPh.range}")
    private BigDecimal soilPh;

    /** 地势（字典 djs_terrain_condition）。 */
    @Size(max = 32, message = "{plant.plot.terrain.size}")
    private String terrainCondition;

    /** 光照（字典 djs_light_condition）。 */
    @Size(max = 32, message = "{plant.plot.light.size}")
    private String lightCondition;

    /** 排水（字典 djs_drain_condition）。 */
    @Size(max = 32, message = "{plant.plot.drain.size}")
    private String drainCondition;

    public interface OnCreate {
    }
}
