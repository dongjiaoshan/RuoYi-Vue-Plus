package org.dromara.djs.plant.plan.domain.vo;

import cn.idev.excel.annotation.ExcelIgnore;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.djs.plant.plan.domain.PlantDetails;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 种植计划明细 VO（PLT-PLAN-001）。
 *
 * <p>{@code plotName / plotCode / cropName / plantTeamName / harvestTeamName} 由 service 批量 enrich。</p>
 *
 * @author djs
 * @since PLT-PLAN-001
 */
@Data
@AutoMapper(target = PlantDetails.class)
public class PlantDetailsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelIgnore
    private Long id;
    @ExcelIgnore
    private Long plantId;
    @ExcelIgnore
    private Long plotId;
    @ExcelIgnore
    private Long cropId;
    @ExcelIgnore
    private Integer plantMonth;
    @ExcelIgnore
    private String plantPeriod;
    @ExcelIgnore
    private LocalDate beginActualdate;
    @ExcelIgnore
    private LocalDate endActualdate;
    @ExcelProperty(value = "开始采摘日期")
    private LocalDate beginHarvestdate;
    @ExcelProperty(value = "结束采摘日期")
    private LocalDate endHarvestdate;
    @ExcelProperty(value = "计划最早采摘")
    private LocalDate earliestHarvestdate;
    @ExcelProperty(value = "计划最晚采摘")
    private LocalDate lastHarvestdate;
    @ExcelIgnore
    private String plantStatus;
    @ExcelProperty(value = "采摘状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_pick_status")
    private String harvestStatus;
    @ExcelProperty(value = "地块面积(亩)")
    private BigDecimal plotArea;
    @ExcelProperty(value = "标准产量(kg)")
    private BigDecimal expectedYield;
    @ExcelProperty(value = "损耗(kg)")
    private BigDecimal lossYield;
    @ExcelProperty(value = "实际产量(kg)")
    private BigDecimal actualYield;
    @ExcelIgnore
    private BigDecimal averageYield;
    @ExcelIgnore
    private Long plantBy;
    @ExcelIgnore
    private Long harvestBy;
    @ExcelProperty(value = "是否采摘活动", converter = ExcelDictConvert.class)
    @ExcelDictFormat(readConverterExp = "1=是,2=否")
    private Integer isPick;
    @ExcelIgnore
    private Date createTime;

    // enrich
    @ExcelProperty(value = "种植地块")
    private String plotName;
    @ExcelProperty(value = "地块编码")
    private String plotCode;
    @ExcelProperty(value = "作物名称")
    private String cropName;
    @ExcelIgnore
    private String plantTeamName;
    @ExcelProperty(value = "种植班组")
    private String harvestTeamName;

    /** 种植日期（取实际开始种植 begin_actualdate；采摘计划详情列展示用）。 */
    @ExcelProperty(value = "种植日期")
    private LocalDate plantDate;
}
