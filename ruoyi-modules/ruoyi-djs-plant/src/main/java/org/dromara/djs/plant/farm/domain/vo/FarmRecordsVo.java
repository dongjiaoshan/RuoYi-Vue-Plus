package org.dromara.djs.plant.farm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.djs.plant.farm.domain.FarmRecords;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 农事记录 VO（PLT-WORK-001）。
 *
 * <p>{@code plotName / plotCode / cropLabel / farmTypeLabel / teamName} 等 enrich 字段由 service 层
 * 批量 N+1 取（参考 PlantPlanServiceImpl#enrichDetails 范式），不写 join SQL。</p>
 *
 * @author djs
 * @since PLT-WORK-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = FarmRecords.class)
public class FarmRecordsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "业务码")
    private String recordNo;

    @ExcelProperty(value = "计划 ID")
    private Long plantId;

    @ExcelProperty(value = "地块 ID")
    private Long plotId;

    /** service enrich：地块名（PlotInfo.plotName）。 */
    @ExcelProperty(value = "地块名称")
    private String plotName;

    /** service enrich：地块码（PlotInfo.plotCode）。 */
    @ExcelProperty(value = "地块编码")
    private String plotCode;

    /** service enrich：转移前地块所属片区名（移栽记录卡展示，PlotZone.zoneName）。 */
    @ExcelProperty(value = "转移前片区")
    private String plotZoneName;

    @ExcelProperty(value = "地块类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_plot_status")
    private Integer plotType;

    @ExcelProperty(value = "作物 ID")
    private Long cropId;

    @ExcelProperty(value = "作物名称")
    private String cropName;

    @ExcelProperty(value = "农事类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_farm_work_type")
    private String farmType;

    @ExcelProperty(value = "农事日期")
    private LocalDate farmDate;

    @ExcelProperty(value = "处理班组 ID")
    private Long farmBy;

    /** service enrich：班组名（PlantWorkTeam.teamName）。 */
    @ExcelProperty(value = "处理班组")
    private String teamName;

    @ExcelProperty(value = "整地类型")
    private String tillageType;

    @ExcelProperty(value = "整地方式")
    private String tillageMethod;

    @ExcelProperty(value = "转移地块 ID")
    private Long transplantPlot;

    /** service enrich：转移地块名（仅 transplant 类型）。 */
    @ExcelProperty(value = "转移地块")
    private String transplantPlotName;

    /** service enrich：转移后地块编号（row101.2：移栽记录卡「转移后」优先显编号，反查 PlotInfo.plotCode）。 */
    @ExcelProperty(value = "转移后地块编号")
    private String transplantPlotCode;

    /** service enrich：转移后地块所属片区名（移栽记录卡展示，PlotZone.zoneName）。 */
    @ExcelProperty(value = "转移后片区")
    private String transplantPlotZoneName;

    @ExcelProperty(value = "移栽百分比")
    private Integer transplantPercent;

    @ExcelProperty(value = "灾害类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_disaster_type")
    private String disasterType;

    @ExcelProperty(value = "损失率(%)")
    private BigDecimal lossRate;

    @ExcelProperty(value = "损失产量")
    private BigDecimal lossYield;

    /** 采摘重量 kg（farm_type=harvest_activity 采摘活动管理录入，FIX-PLT-MP-HARVEST-001 #3=a）。 */
    @ExcelProperty(value = "采摘重量(kg)")
    private BigDecimal harvestWeight;

    @ExcelProperty(value = "预警", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_yes_no")
    private Integer isWarning;

    private String proofOssIds;

    private String remark;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

    private Long createBy;
}
