package org.dromara.djs.plant.pick.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
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
@ExcelIgnoreUnannotated
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

    // ===== DENGBO-R4 采摘去向 5 列（按 crop_id + activity_date 聚合 t_plant_plant_activity.pick_dest）=====

    /** 销售重量（pick_dest='sale' 或历史 NULL 行的 pick_weight 之和，kg）。 */
    @ExcelProperty(value = "销售重量(kg)")
    private BigDecimal saleWeight;

    /** 毛菜处理间量（pick_dest='veg_fresh'，kg）。 */
    @ExcelProperty(value = "毛菜处理间量(kg)")
    private BigDecimal vegFreshWeight;

    /** 果蔬月台量（pick_dest='platform'，kg）。 */
    @ExcelProperty(value = "果蔬月台量(kg)")
    private BigDecimal platformWeight;

    /** 损耗量（pick_dest='loss'，kg）。 */
    @ExcelProperty(value = "损耗量(kg)")
    private BigDecimal lossWeight;

    /** 饲料饲喂量（pick_dest='feed'，kg）。 */
    @ExcelProperty(value = "饲料饲喂量(kg)")
    private BigDecimal feedWeight;
}
