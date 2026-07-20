package org.dromara.djs.plant.overview.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 种植总览-作物详情下钻行 VO（FIX-PLT-AD-OVERVIEW-001 图2，14 列逐地块明细）。
 *
 * <p>按 {@code cropId} 聚合 {@code t_plant_plant_details} 逐地块行（一地块一计划阶段一行），
 * JOIN 出地块名 / 种植班组名 / 种植季 / 计划模糊文案。纯只读，未发生日期前端显 {@code -}。</p>
 *
 * <p>列顺序严格对齐原型（导出列同序）：作物名称 / 种植地块 / 采摘状态 / 种植季 / 计划种植日期 /
 * 种植日期 / 种植人员 / 实际开始采摘日期 / 实际结束采摘日期 / 最早起始采摘时间 / 最晚截止采摘时间 /
 * 地块面积 / 标准产量 / 实际采收量。</p>
 *
 * <p>单位：面积亩，产量 <b>kg</b>（决策#7）。</p>
 *
 * @author djs
 * @since FIX-PLT-AD-OVERVIEW-001
 */
@Data
@ExcelIgnoreUnannotated
public class CropDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 明细 id（前端 row-key 用，不导出）。 */
    private Long id;

    /** 1 作物名称。 */
    @ExcelProperty(value = "作物名称")
    private String cropName;

    /** 2 地块编号（plot_code，用户手填业务码）。 */
    @ExcelProperty(value = "地块编号")
    private String plotCode;

    /** 3 种植地块（plot_name）。 */
    @ExcelProperty(value = "种植地块")
    private String plotName;

    /** 3 种植状态（字典 djs_plant_plan_status：pending/ongoing/completed）。 */
    @ExcelProperty(value = "种植状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_plant_plan_status")
    private String plantStatus;

    /** 4 采摘状态（字典 djs_pick_status）。 */
    @ExcelProperty(value = "采摘状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_pick_status")
    private String harvestStatus;

    /** 4 种植季（字典 djs_planting_season，来源计划主表 plan_season）。 */
    @ExcelProperty(value = "种植季", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_planting_season")
    private String planSeason;

    /** 5 计划种植日期（模糊文案，例 "4月中旬"，由明细行 plant_month + plant_period 拼接）。 */
    @ExcelProperty(value = "计划种植日期")
    private String planPlantDate;

    /** 6 种植日期（实际开始种植日，YYYY-MM-DD，未发生 null）。 */
    @ExcelProperty(value = "种植日期")
    private LocalDate plantDate;

    /** 7 种植人员（种植班组名 plant_by → team_name）。 */
    @ExcelProperty(value = "种植人员")
    private String plantTeamName;

    /** 8 实际开始采摘日期（null 未发生）。 */
    @ExcelProperty(value = "实际开始采摘日期")
    private LocalDate beginHarvestdate;

    /** 9 实际结束采摘日期（null 未发生）。 */
    @ExcelProperty(value = "实际结束采摘日期")
    private LocalDate endHarvestdate;

    /** 10 最早起始采摘时间（计划最早采摘日 earliest_harvestdate）。 */
    @ExcelProperty(value = "最早起始采摘时间")
    private LocalDate earliestHarvestdate;

    /** 11 最晚截止采摘时间（计划最晚采摘日 last_harvestdate）。 */
    @ExcelProperty(value = "最晚截止采摘时间")
    private LocalDate lastHarvestdate;

    /** 12 地块面积（亩）。 */
    @ExcelProperty(value = "地块面积(亩)")
    private BigDecimal plotArea;

    /** 13 标准产量（kg，预计产量 expected_yield）。 */
    @ExcelProperty(value = "标准产量(kg)")
    private BigDecimal expectedYield;

    /** 14 实际采收量（kg，actual_yield）。 */
    @ExcelProperty(value = "实际采收量(kg)")
    private BigDecimal actualYield;
}
