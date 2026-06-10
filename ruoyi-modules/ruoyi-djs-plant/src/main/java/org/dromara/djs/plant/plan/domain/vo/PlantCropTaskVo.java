package org.dromara.djs.plant.plan.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 当月播种任务「作物级聚合」卡 VO（FIX-PLT-MP-SEED-002，mp 播种首页「种植任务」tab）。
 *
 * <p>D2「拆两接口」的聚合侧：以当月种植明细按 {@code crop_id} GROUP BY，喂首页作物卡
 * + 「全部(N)」计数（= 作物数）+ 完成率。逐块开工走另一接口 {@code selectMonthTasks}
 * （明细级，保留 {@code begin_actualdate} + 明细 id）。</p>
 *
 * <p>完成率口径（D1 = 开工进度）：{@code startedPlotCount / plotCount × 100}，
 * 即该作物已开工地块数 / 计划地块数；与详情页、顶部 KPI 三处统一为「开工口径」，
 * 不再用 {@code actual_yield}（采摘才写，播种期恒 0）。前端按两值算，避免后端除零。</p>
 *
 * @author djs
 * @since FIX-PLT-MP-SEED-002
 */
@Data
public class PlantCropTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物 ID（雪花，{@code t_plant_crop_info.id}；前端按此跳详情页）。 */
    private Long cropId;

    /** 作物名称（如 白菜）。 */
    private String cropName;

    /** 作物缩略图 OSS objectName（可空，前端回落统一占位图）。 */
    private String cropImg;

    /**
     * 片区名称（按该作物当月明细聚合；多片区以「、」拼接，全空 → null 前端归「未分区」）。
     * mp 首页片区 chips 仍按明细维度自算，本字段供卡片展示。
     */
    private String zoneName;

    /** 该作物当月计划种植总面积（亩，明细 plot_area 累加）。 */
    private BigDecimal totalArea;

    /** 该作物当月计划地块数（完成率分母）。 */
    private Integer plotCount;

    /** 该作物当月已开工地块数（{@code begin_actualdate IS NOT NULL}；完成率分子）。 */
    private Integer startedPlotCount;

    /** 该作物最早计划月份 1-12（卡片「计划」展示用）。 */
    private Integer plantMonth;

    /** 该作物最早计划阶段 CHAR(2)（djs_plant_period：05=上旬 / 15=中旬 / 25=下旬）。 */
    private String plantPeriod;

    /** 计划自由文本（plan.plant_date，例 "4月上旬"，优先展示，空则前端按 month+period 拼）。 */
    private String planDate;
}
