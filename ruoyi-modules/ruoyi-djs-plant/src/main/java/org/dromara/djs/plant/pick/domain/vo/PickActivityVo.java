package org.dromara.djs.plant.pick.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采摘活动只读聚合报表 VO（每日采摘统计）。
 *
 * <p>按「作物 + 活动日期」聚合 {@code t_plant_plant_details}，逐行一条「某作物某天」的采摘汇总。
 * 仅查询 + 导出，无写操作。</p>
 *
 * @author djs
 */
@Data
public class PickActivityVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 作物 id（前端作物名称下拉回显用，不导出）。 */
    private Long cropId;

    @ExcelProperty(value = "活动日期")
    private LocalDate activityDate;

    @ExcelProperty(value = "作物名称")
    private String cropName;

    /** 活动地块数（COUNT DISTINCT plot_id）。 */
    @ExcelProperty(value = "活动地块数")
    private Integer plotCount;

    /** 今日采摘重量（当日 SUM actual_yield，kg）。 */
    @ExcelProperty(value = "今日采摘重量(kg)")
    private BigDecimal todayPickWeight;

    /** 预计总产量（SUM expected_yield，kg）。 */
    @ExcelProperty(value = "预计总产量(kg)")
    private BigDecimal expectedYield;

    /** 累计已采重量（截至该日累计 SUM actual_yield，kg）。 */
    @ExcelProperty(value = "累计已采重量(kg)")
    private BigDecimal cumulativePickWeight;
}
