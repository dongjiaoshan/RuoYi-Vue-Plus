package org.dromara.djs.plant.plot.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.plant.plot.domain.PlotInfo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 地块视图对象（PLT-MD-001）。
 *
 * <p>{@code zoneName} 由 service 层 N+1 取（V1 数据量小 &lt; 100 地块；V2 改 LEFT JOIN）。</p>
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PlotInfo.class)
public class PlotInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "地块编码")
    private String plotCode;

    private String plotImagePreview;

    private String plotImageUrl;

    @ExcelProperty(value = "所属片区 ID")
    private Long zoneId;

    /** 所属片区名（service 层 enrich）。 */
    @ExcelProperty(value = "所属片区")
    private String zoneName;

    /** 所属大区（service 层 enrich，取自所属片区 zone.zoneBelong；字典 djs_zone_belong）。 */
    @ExcelProperty(value = "所属大区", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_zone_belong")
    private String zoneBelong;

    @ExcelProperty(value = "类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_plot_type")
    private String plotType;

    @ExcelProperty(value = "地块名称")
    private String plotName;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_plot_status")
    private Integer plotStatus;

    @ExcelProperty(value = "是否租赁", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_yes_no")
    private Integer isLease;

    private String plotRemark;

    @ExcelProperty(value = "面积(亩)")
    private BigDecimal plotArea;

    private String plotLocationDesc;
    private BigDecimal plotLocationX;
    private BigDecimal plotLocationY;

    @ExcelProperty(value = "土壤类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_soil_type")
    private String soilType;

    @ExcelProperty(value = "土壤肥力", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_soil_fertility")
    private String soilFertility;

    @ExcelProperty(value = "PH")
    private BigDecimal soilPh;

    @ExcelProperty(value = "地势", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_terrain_condition")
    private String terrainCondition;

    @ExcelProperty(value = "光照", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_light_condition")
    private String lightCondition;

    @ExcelProperty(value = "排水", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_drain_condition")
    private String drainCondition;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 更新时间。
     */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;

    /**
     * 更新人 ID（{@code sys_user.user_id}），供 {@link Translation} 反射取数翻译成 updateByName。
     */
    private Long updateBy;

    /**
     * 更新人姓名（注解翻译，VO 序列化时填）。
     */
    @ExcelProperty(value = "更新人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "updateBy")
    private String updateByName;

    /**
     * 当前作物名（service 层动态计算）。
     *
     * <p>D8 阶段 {@code t_plant_plant_details} 0 行，返回 null；D9+ PLT-PLAN-001 上线后启用 JOIN。</p>
     */
    private String currentCropName;

    /** 历史种植次数（service enrich，按 plot_id COUNT t_plant_plant_details）。 */
    private Long historyPlantCount;

    /** 最高亩产作物名（service enrich，取该地块 average_yield 最大行 crop_name）。 */
    private String maxYieldCropName;

    /** 最高亩作物产量 kg/亩（service enrich，MAX average_yield）。 */
    private BigDecimal maxYieldPerMu;
}
