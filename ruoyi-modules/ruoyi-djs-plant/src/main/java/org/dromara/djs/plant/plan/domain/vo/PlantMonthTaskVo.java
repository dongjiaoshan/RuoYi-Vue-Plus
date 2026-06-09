package org.dromara.djs.plant.plan.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 当月播种任务卡 VO（FIX-PLANT-SEED-001，mp 播种首页「种植任务」tab）。
 *
 * <p>以 {@code t_plant_plant_details} 当月明细为基，JOIN
 * {@code t_plant_plot_info}（片区 / 面积）+ {@code t_plant_crop_info}（作物名 / 图）+
 * {@code t_plant_plant_plan}（计划日期），按片区分组喂原型 59 任务卡。</p>
 *
 * <p>每张卡 = 作物图 + 作物名 + 完成率（actual/expected）+ 面积（亩）+ 计划日期 + 地块 + 片区。
 * 完成率由前端按 {@code actualYield / expectedYield} 计算（VO 直接给两值，避免后端除零）。</p>
 *
 * @author djs
 * @since FIX-PLANT-SEED-001
 */
@Data
public class PlantMonthTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 明细 ID（雪花，{@code t_plant_plant_details.id}）。 */
    private Long id;

    /** 计划 ID（{@code t_plant_plant_plan.id}）。 */
    private Long plantId;

    /** 计划单号（业务码 PLAN-yyyy-NNN）。 */
    private String planNo;

    /** 地块 ID。 */
    private Long plotId;

    /** 地块业务码（如 PLOT-A-01）。 */
    private String plotCode;

    /** 地块名称。 */
    private String plotName;

    /** 片区 ID。 */
    private Long zoneId;

    /** 片区名称（按此分组，如 A 区 / B 区）。 */
    private String zoneName;

    /** 作物 ID。 */
    private Long cropId;

    /** 作物名称（如 白菜）。 */
    private String cropName;

    /** 作物缩略图 OSS objectName（可空，前端回落统一占位图）。 */
    private String cropImg;

    /** 地块面积（亩）。 */
    private BigDecimal area;

    /** 计划月份 1-12。 */
    private Integer plantMonth;

    /** 实际开始种植日期（FIX-PLT-MP-SEED-001：详情页计划地块 list 显示「实际开始时间」；未开工为 null）。 */
    private LocalDate beginActualdate;

    /**
     * 计划阶段 CHAR(2)（字典 {@code djs_plant_period}：05=上旬 / 15=中旬 / 25=下旬）。
     * 前端按 plantMonth + plantPeriod 映射「N 月上旬/中旬/下旬」展示。
     */
    private String plantPeriod;

    /** 计划自由文本（plan.plant_date，例 "4月上旬"，优先展示，空则前端按 month+period 拼）。 */
    private String planDate;

    /** 预计产量（kg，= plot_area × crop.predicted_per）。 */
    private BigDecimal expectedYield;

    /** 实际产量（kg，采摘累计；前端 actual/expected 算完成率）。 */
    private BigDecimal actualYield;

    /** 种植状态（字典 {@code djs_plant_plan_status}）。 */
    private String plantStatus;

}
