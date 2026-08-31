package org.dromara.djs.plant.plan.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.djs.common.excel.DictOrRawConvert;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.djs.plant.plan.domain.PlantPlan;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 种植计划列表视图对象（PLT-PLAN-001 / FIX-PLT-AD-PLAN-001 对齐原型 14 列）。
 *
 * <p>enrich 分两类（均在 {@code PlantPlanServiceImpl.queryPageList} 分页后批量回填，避免 SQL JOIN）：</p>
 * <ul>
 *   <li>{@code cropName} / {@code cropImage}：按 cropId 批量查 crop_info</li>
 *   <li>{@code expectedYield} / {@code actualYield} / {@code finishedPlot} / {@code completionRate}
 *       / {@code earliestBegindate} / {@code lastBegindate}：按 planId 批量聚合 t_plant_plant_details</li>
 * </ul>
 *
 * <p>{@code createByName} 走 ruoyi {@code USER_ID_TO_NICKNAME} 注解翻译（取 sys_user.nick_name）。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PlantPlan.class)
public class PlantPlanVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "计划号")
    private String planNo;

    @ExcelProperty(value = "计划年份")
    private Integer planYear;

    private Long cropId;

    /** enrich 字段（mapper SQL 不查；service 后填）。 */
    @ExcelProperty(value = "作物")
    private String cropName;

    /** 种植图片 ossId（crop_info 的 image_oss_id → crop_image_preview → crop_image_url 首段兜底，service enrich）。 */
    private String cropImage;

    private String plantDate;

    @ExcelProperty(value = "季节", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_planting_season")
    private String planSeason;

    /** 计划最早开始日期 = MIN(details: planYear+plantMonth+plantPeriod 推算)（service enrich）。 */
    @ExcelProperty(value = "最早开始")
    private LocalDate earliestBegindate;

    /** 计划最晚开始日期 = MAX(details: planYear+plantMonth+plantPeriod 推算)（service enrich）。 */
    @ExcelProperty(value = "最晚开始")
    private LocalDate lastBegindate;

    /** 计划种植月份（最早开始那条明细的 plant_month，service enrich）；列表"计划种植日期"列用。 */
    @ExcelProperty(value = "计划种植月份")
    private Integer plantMonth;

    /** 计划种植旬别（最早开始那条明细的 plant_period 05/15/25，service enrich，dict djs_plant_period）。 */
    @ExcelProperty(value = "计划种植旬别", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_plant_period")
    private String plantPeriod;

    @ExcelProperty(value = "最早采摘")
    private LocalDate earliestHarvestdate;

    @ExcelProperty(value = "最晚采摘")
    private LocalDate lastHarvestdate;

    @ExcelProperty(value = "总面积(亩)")
    private BigDecimal totalArea;

    @ExcelProperty(value = "地块数")
    private Integer totalPlot;

    /** 预计产量 kg = SUM(details.expected_yield)（service enrich）。 */
    @ExcelProperty(value = "预计产量(kg)")
    private BigDecimal expectedYield;

    /** 实际产量 kg = SUM(details.actual_yield)（service enrich）。 */
    @ExcelProperty(value = "实际产量(kg)")
    private BigDecimal actualYield;

    /** 已完成种植地数量 = COUNT(details: plant_status='completed')（service enrich）。 */
    @ExcelProperty(value = "已完成地块数")
    private Integer finishedPlot;

    /** 计划完成率(%) = finishedPlot / totalPlot × 100，0~100（service enrich）。 */
    @ExcelProperty(value = "完成率(%)")
    private BigDecimal completionRate;

    @ExcelProperty(value = "状态", converter = DictOrRawConvert.class)
    @ExcelDictFormat(dictType = "djs_plant_plan_status")
    private String plantStatus;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /** 计划更新时间（entity 映射）。 */
    @ExcelProperty(value = "更新时间")
    private Date updateTime;

    /** 编制人 ID（sys_user.user_id），供 {@link Translation} 反射翻译成 createByName。 */
    private Long createBy;

    /** 计划编制人姓名（注解翻译，VO 序列化时填）。 */
    @ExcelProperty(value = "编制人")
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String createByName;
}
