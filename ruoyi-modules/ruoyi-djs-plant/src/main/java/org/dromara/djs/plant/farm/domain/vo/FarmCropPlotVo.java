package org.dromara.djs.plant.farm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 农事多选页「作物 → 种植地块」候选卡 VO（FIX-PLT-MP-WORK-BATCH-001，#2）。
 *
 * <p>原型作物详情多选层每行：地块编号 + 间隔天数（距上次同类农事）+ 上次时间。
 * 退茬（rotation）只列 {@code plant_status='completed'}（采摘完成）的地块。</p>
 *
 * @author djs
 * @since FIX-PLT-MP-WORK-BATCH-001
 */
@Data
public class FarmCropPlotVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 计划明细 id（{@code t_plant_plant_details.id}，前端选中后回填 plantId/plotId/cropId 三元组）。 */
    private Long detailId;

    /** 关联种植计划 id。 */
    private Long plantId;

    /** 地块 id。 */
    private Long plotId;

    /** 地块业务码（如 PLOT-A-01，原型「地块编号」）。 */
    private String plotCode;

    /** 地块名称。 */
    private String plotName;

    /** 所属片区业务码（join t_plant_plot_zone，退茬多选页展示片区，FIX-PLT-MP-ROTATION-ZONE-001）。 */
    private String zoneCode;

    /** 所属片区名称（join t_plant_plot_zone）。 */
    private String zoneName;

    /** 作物 id。 */
    private Long cropId;

    /** 上次同类农事日期（无则 null）。 */
    private LocalDate lastFarmDate;

    /** 距上次同类农事的间隔天数（lastFarmDate 为空时 null）。 */
    private Integer intervalDays;

    /** 种植状态（字典 {@code djs_plant_plan_status}：pending/ongoing/completed）。 */
    private String plantStatus;

    /**
     * 累计移栽百分比 0-100（仅 farmType=transplant 返回，FIX-PLT-MP-WORK-BATCH-001 移栽进度层）。
     *
     * <p>= 该地块下 farm_type=transplant 历史 transplant_percent 之和（已移 60%/80%/0%）。
     * 移栽单块录入弹窗按此校验"最多可移 100 − transplantedPercent"。</p>
     */
    private Integer transplantedPercent;

    /** 采摘状态（字典 {@code djs_pick_status}：pending/picking/completed），退茬多选页每行展示。 */
    private String harvestStatus;

    /** 计划最早采摘日期。 */
    private LocalDate earliestHarvestdate;

    /** 计划最晚采摘日期。 */
    private LocalDate lastHarvestdate;

    /** 实际结束采摘日期（采摘完成时间，退茬多选页「完成时间」列）。 */
    private LocalDate endHarvestdate;

    /**
     * 灾害记录（r56，仅 farmType=disaster 返回）：同一地块可多次灾害录入，按 farm_date 倒序取最近 3 条，
     * mp 灾害选地块页在地块名下逐行展示「灾害 损失率% 录入时间」。
     */
    private List<DisasterRecord> disasterRecords;

    /** 单条灾害记录（r56）。disasterType 原始码值，前端按 {@code djs_disaster_type} 字典翻译展示。 */
    @Data
    public static class DisasterRecord implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 灾害类型（字典 {@code djs_disaster_type} 码值；前端翻译，缺则展示「灾害」）。 */
        private String disasterType;

        /** 损失率 0-100。 */
        private BigDecimal lossRate;

        /** 录入时间（farm_date）。 */
        private LocalDate farmDate;
    }
}
