package org.dromara.djs.plant.market.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 果蔬上市计划行 VO（V6-R151 建，V6-R157/R158 改口径）。一行 = 一条种植计划 {@code t_plant_plant_plan}。
 *
 * <h3>上市 / 下架日期口径</h3>
 * 逐条采摘明细先取「实际优先、计划兜底」的有效日期，再对整条计划取 MIN / MAX：
 * <ul>
 *   <li>上市日期 = {@code MIN(COALESCE(明细.begin_harvestdate, 明细.earliest_harvestdate))}</li>
 *   <li>下架日期 = {@code MAX(COALESCE(明细.end_harvestdate,   明细.last_harvestdate))}</li>
 * </ul>
 * {@code begin_harvestdate / end_harvestdate} 是 mp 采摘触发写入的<b>实际</b>起止采摘日期，
 * {@code earliest_harvestdate / last_harvestdate} 是建计划时按作物生长周期推的<b>计划</b>日期。
 * 精确到天，不再截到月。
 *
 * <p>不读主表冗余列 {@code t_plant_plant_plan.earliest_harvestdate / last_harvestdate}：
 * 采摘计划侧改明细采摘日期后不回刷主表聚合，主表值会过期。查询时从明细现算。</p>
 *
 * <p>没排过采摘明细的计划，两个日期为 {@code null}，该行仍在列表里（前端显 {@code -}），状态同样为空。</p>
 *
 * <h3>导出</h3>
 * 带 {@code @ExcelProperty} 的字段按声明序即导出列序：
 * 状态 / 作物名称 / 预计产量 / 实际产量 / 上市日期 / 下架日期。
 * 作物图片列只上列表、<b>不进导出</b>（V6-R157 甲方点名去掉）。
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

    /** 列表第 1 列 作物图片（service 用 ImageUrlResolver 批量解析出的 OSS URL；无图为 null）。不进导出。 */
    private String cropImageUrl;

    /**
     * 列表第 2 列 状态码（service 按 {@code marketBeginDate / marketEndDate} 与当天现算，不落库）。
     *
     * <p>取值见 {@link org.dromara.djs.plant.market.util.MarketStatusCalculator}：
     * {@code pending / upcoming / on_sale / ending / off_shelf}；上市日期为空时为 {@code null}。
     * 前端按码查 i18n 显示中文，导出走下面的 {@code marketStatusName}。</p>
     */
    private String marketStatus;

    /** 1 状态中文名（仅导出用；前端不读它，前端按 {@code marketStatus} 走 i18n）。 */
    @ExcelProperty(value = "状态")
    private String marketStatusName;

    /** 2 作物名称。 */
    @ExcelProperty(value = "作物名称")
    private String cropName;

    /** 3 预计产量（kg，逐地块扣灾害损失钳零后求和）。 */
    @ExcelProperty(value = "预计产量(kg)")
    private BigDecimal expectedYield;

    /** 4 实际产量（kg，明细 actual_yield 求和）。 */
    @ExcelProperty(value = "实际产量(kg)")
    private BigDecimal actualYield;

    /** 5 上市日期 yyyy-MM-dd（无采摘明细时 null）。 */
    @ExcelProperty(value = "上市日期")
    private String marketBeginDate;

    /** 6 下架日期 yyyy-MM-dd（无采摘明细时 null）。 */
    @ExcelProperty(value = "下架日期")
    private String marketEndDate;
}
