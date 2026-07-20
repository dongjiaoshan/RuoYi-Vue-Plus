package org.dromara.djs.plant.pick.domain.vo;

import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采摘计划列表行 VO（按作物聚合）。
 *
 * <p>按作物聚合 {@code t_plant_plant_details}（对齐原型「采摘计划列表」按作物维度）：</p>
 * <pre>
 *   SELECT crop_id,
 *          COUNT(DISTINCT plot_id)        AS plot_count,
 *          MIN(earliest_harvestdate)      AS plan_earliest,
 *          MAX(last_harvestdate)          AS plan_latest,
 *          SUM(plot_area)                 AS total_acreage,   -- 当前种植亩数
 *          SUM(expected_yield)            AS expected_yield,
 *          SUM(actual_yield WHERE 当年)   AS actual_yield,     -- 当年已采摘量
 *          (灾害农事记录 farm_type='disaster' 按作物 SUM(loss_yield)) AS disaster_loss,  -- 预计灾害损失量
 *          COUNT(DISTINCT plot_id WHERE 当年) AS plot_total_count,
 *          SUM(is_pick=1)                 AS activity_plot_count
 *   GROUP BY crop_id;
 * </pre>
 *
 * @author djs
 */
@Data
public class PickPlanGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelIgnore
    private Long cropId;

    @ExcelProperty(value = "作物名称")
    private String cropName;

    /** 作物主图 L1 ossId（mapper 透传，Service enrich 后转 {@link #cropImageUrl}）。 */
    @JsonIgnore
    @ExcelIgnore
    private String imageOssId;

    /** 作物主图 public URL（IMG-LIB-001 resolver 兜底回填）。 */
    @ExcelIgnore
    private String cropImageUrl;

    /** 涉及地块数（COUNT DISTINCT plot_id）。 */
    @ExcelIgnore
    private Integer plotCount;

    /** 计划最早采摘（MIN earliest_harvestdate）。 */
    @ExcelProperty(value = "最早采摘日期")
    private LocalDate planEarliest;

    /** 计划最晚采摘（MAX last_harvestdate）。 */
    @ExcelProperty(value = "最晚采摘日期")
    private LocalDate planLatest;

    /** 计划种植亩数（SUM plot_area，亩）。 */
    @ExcelProperty(value = "计划种植亩数")
    private BigDecimal totalAcreage;

    /** 当前已种植亩数（SUM plot_area WHERE begin_actualdate IS NOT NULL，亩；已实际开始种植的地块面积）。 */
    @ExcelProperty(value = "当前已种植亩数")
    private BigDecimal currentPlantedArea;

    /** 预计需求量（kg；无来源时为 0/NULL，前端 - 兜底）。 */
    @ExcelIgnore
    private BigDecimal demandQty;

    /** 预计产量（SUM expected_yield，kg；未扣灾害的理论标准产量）。 */
    @ExcelProperty(value = "预计产量(kg)")
    private BigDecimal expectedYield;

    /**
     * 预计净产量（kg）= max(0, 预计产量 − 预计灾害损失量)。
     *
     * <p>row185：发生灾害时「预计产量」需扣除该作物灾害损失（按 crop_id 聚合的灾害 SUM），钳 0 防负。
     * 前端列表「预计产量」列展示此净值；原始 {@link #expectedYield} / {@link #disasterLoss} 仍保留供核对。</p>
     */
    @ExcelProperty(value = "预计净产量(kg)")
    private BigDecimal netExpectedYield;

    /** 当年已采摘量（当年累计实采 SUM actual_yield，kg）。 */
    @ExcelProperty(value = "当年已采摘量(kg)")
    private BigDecimal actualYield;

    /** 预计灾害损失量（灾害农事记录 farm_type='disaster' 按作物 SUM(loss_yield)，kg）。 */
    @ExcelProperty(value = "预计灾害损失量(kg)")
    private BigDecimal disasterLoss;

    /** 当年种植地块总数（当年 COUNT DISTINCT plot_id）。 */
    @ExcelProperty(value = "当年种植地块总数")
    private Integer plotTotalCount;

    /** 已设为采摘活动的地块数（SUM is_pick=1）。 */
    @ExcelProperty(value = "采摘活动地块数")
    private Integer activityPlotCount;
}
