package org.dromara.djs.plant.overview.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 种植总览-作物卡片导出行 VO（row147）。
 *
 * <p>一作物一行、横向展示，列顺序与页面卡片一致：作物名称 / 作物编号 / 计划完成率 /
 * 计划地块数 / 计划面积 / 预计产量 / 已种植地块数 / 已种植面积 / 已产产量。</p>
 *
 * <p>单位：面积亩，产量 <b>kg</b>（与卡片同口径，不换算吨）。计划完成率为数值（2 位小数），
 * 表头带 {@code (%)}，便于在 Excel 里排序求平均。</p>
 *
 * @author djs
 * @since row147
 */
@Data
@ExcelIgnoreUnannotated
public class CropOverviewExportVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 1 作物名称。 */
    @ExcelProperty(value = "作物名称")
    private String cropName;

    /** 2 作物编号（crop_code，卡片上随作物名展示，区分同名不同编码作物）。 */
    @ExcelProperty(value = "作物编号")
    private String cropCode;

    /** 3 计划完成率（% 数值，= 已种植地块数 / 计划地块数 * 100，2 位小数）。 */
    @ExcelProperty(value = "计划完成率(%)")
    private BigDecimal completionRate;

    /** 4 计划地块数。 */
    @ExcelProperty(value = "计划地块数")
    private Integer planPlotCount;

    /** 5 计划面积（亩）。 */
    @ExcelProperty(value = "计划面积(亩)")
    private BigDecimal planArea;

    /** 6 预计产量（kg）。 */
    @ExcelProperty(value = "预计产量(kg)")
    private BigDecimal planExpectedYield;

    /** 7 已种植地块数。 */
    @ExcelProperty(value = "已种植地块数")
    private Integer donePlotCount;

    /** 8 已种植面积（亩）。 */
    @ExcelProperty(value = "已种植面积(亩)")
    private BigDecimal doneArea;

    /** 9 已产产量（kg）。 */
    @ExcelProperty(value = "已产产量(kg)")
    private BigDecimal doneHarvestYield;
}
