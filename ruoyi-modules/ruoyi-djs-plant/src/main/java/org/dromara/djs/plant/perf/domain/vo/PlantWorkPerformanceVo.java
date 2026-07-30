package org.dromara.djs.plant.perf.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.djs.plant.perf.domain.PlantWorkPerformance;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 班组绩效视图对象（PLT-PERF-001）。
 *
 * <p>{@code teamName} / {@code cropName} 是 service 层批量 enrich 的冗余列
 * （反查 t_plant_work_team.team_name / t_plant_crop_info.crop_name，避免 N+1）。</p>
 *
 * @author djs
 * @since PLT-PERF-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PlantWorkPerformance.class)
public class PlantWorkPerformanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绩效行 ID。
     */
    @ExcelProperty(value = "ID")
    private Long id;

    /**
     * 统计月份。
     */
    @ExcelProperty(value = "月份")
    private String statMonth;

    /**
     * 班组 ID。
     */
    private Long teamId;

    /**
     * 班组名称（冗余，反查 t_plant_work_team.team_name）。
     */
    @ExcelProperty(value = "班组")
    private String teamName;

    /**
     * 作物 ID。
     */
    private Long cropId;

    /**
     * 作物名称（冗余，反查 t_plant_crop_info.crop_name）。
     */
    @ExcelProperty(value = "作物")
    private String cropName;

    /**
     * 采摘总量（公斤）。
     */
    @ExcelProperty(value = "采摘量(公斤)")
    private BigDecimal pickWeight;

    /**
     * 单价快照（元/斤）。
     */
    @ExcelProperty(value = "单价快照(元/斤)")
    private BigDecimal unitPriceSnapshot;

    /**
     * 应付绩效金额（元）。
     */
    @ExcelProperty(value = "应付金额(元)")
    private BigDecimal performanceAmount;

    /**
     * 绩效规则描述。
     */
    @ExcelProperty(value = "绩效规则")
    private String performanceRule;

    /**
     * 备注。
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间。
     */
    @ExcelProperty(value = "生成时间")
    private Date createTime;
}
