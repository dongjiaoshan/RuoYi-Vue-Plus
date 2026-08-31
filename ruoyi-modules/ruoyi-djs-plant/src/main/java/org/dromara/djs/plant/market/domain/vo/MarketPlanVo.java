package org.dromara.djs.plant.market.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 果蔬上市计划行 VO（V6-R151）。一行 = 一条种植计划 {@code t_plant_plant_plan}。
 *
 * <h3>上市 / 下市月份口径</h3>
 * 上市月份 = {@code MIN(t_plant_plant_details.earliest_harvestdate)} 所在月；
 * 下市月份 = {@code MAX(t_plant_plant_details.last_harvestdate)} 所在月。
 * 即种植计划页「最早开始采摘日期 / 最晚结束采摘日期」两列的月份，两页数值同源，不会互相打架。
 *
 * <p>不读主表冗余列 {@code t_plant_plant_plan.earliest_harvestdate / last_harvestdate}：
 * 采摘计划侧改明细采摘日期后不回刷主表聚合，主表值会过期。查询时从明细现算。</p>
 *
 * <p>没排过采摘明细的计划，两个月份为 {@code null}，该行仍在列表里（前端显 {@code -}）。</p>
 *
 * <h3>导出</h3>
 * 带 {@code @ExcelProperty} 的 6 个字段按声明序即导出列序，与列表列一一对应：
 * 作物图片 / 作物名称 / 预计产量 / 实际产量 / 上市月份 / 下市月份。
 * 「作物图片」列写 OSS URL 文本（本仓库既有导出无嵌图先例，不引新 converter）。
 *
 * @author djs
 */
@Data
@ExcelIgnoreUnannotated
public class MarketPlanVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 种植计划 id（前端 row-key；Long 全局序列化成 string）。 */
    private Long planId;

    /** 计划编号 PLAN-yyyy-NNN（不上列表，排查用）。 */
    private String planNo;

    /** 计划年份（不上列表，排查用）。 */
    private Integer planYear;

    /** 作物 id。 */
    private Long cropId;

    /** 作物主图 ossId（三级兜底解析结果，前端不直接用，排查用）。 */
    private String cropImage;

    /** 1 作物图片（service 用 ImageUrlResolver 批量解析出的 OSS URL；无图为 null）。 */
    @ExcelProperty(value = "作物图片")
    private String cropImageUrl;

    /** 2 作物名称。 */
    @ExcelProperty(value = "作物名称")
    private String cropName;

    /** 3 预计产量（kg，逐地块扣灾害损失钳零后求和）。 */
    @ExcelProperty(value = "预计产量(kg)")
    private BigDecimal expectedYield;

    /** 4 实际产量（kg，明细 actual_yield 求和）。 */
    @ExcelProperty(value = "实际产量(kg)")
    private BigDecimal actualYield;

    /** 5 上市月份 yyyy-MM（无采摘明细时 null）。 */
    @ExcelProperty(value = "上市月份")
    private String marketBeginMonth;

    /** 6 下市月份 yyyy-MM（无采摘明细时 null）。 */
    @ExcelProperty(value = "下市月份")
    private String marketEndMonth;
}
