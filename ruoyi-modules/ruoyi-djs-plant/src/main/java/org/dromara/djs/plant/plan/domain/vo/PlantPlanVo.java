package org.dromara.djs.plant.plan.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.djs.plant.plan.domain.PlantPlan;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 种植计划列表视图对象（PLT-PLAN-001）。
 *
 * <p>{@code cropName} 由 {@code PlantPlanServiceImpl.queryPageList} 之后批量 enrich（避免 SQL JOIN 复杂化）。</p>
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

    private String plantDate;

    @ExcelProperty(value = "季节", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_planting_season")
    private String planSeason;

    @ExcelProperty(value = "最早采摘")
    private LocalDate earliestHarvestdate;

    @ExcelProperty(value = "最晚采摘")
    private LocalDate lastHarvestdate;

    @ExcelProperty(value = "总面积(亩)")
    private BigDecimal totalArea;

    @ExcelProperty(value = "地块数")
    private Integer totalPlot;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_plant_plan_status")
    private String plantStatus;

    @ExcelProperty(value = "创建时间")
    private Date createTime;
}
