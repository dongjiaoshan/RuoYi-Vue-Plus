package org.dromara.djs.plant.plot.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.excel.annotation.ExcelDictFormat;
import org.dromara.common.excel.convert.ExcelDictConvert;
import org.dromara.djs.plant.plot.domain.PlotInfo;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 地块视图对象（PLT-MD-001）。
 *
 * <p>{@code zoneName} 由 service 层 N+1 取（V1 数据量小 &lt; 100 地块；V2 改 LEFT JOIN）。</p>
 *
 * @author djs
 * @since PLT-MD-001
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PlotInfo.class)
public class PlotInfoVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "ID")
    private Long id;

    @ExcelProperty(value = "地块编码")
    private String plotCode;

    private String plotImagePreview;

    private String plotImageUrl;

    @ExcelProperty(value = "所属片区 ID")
    private Long zoneId;

    /** 所属片区名（service 层 enrich）。 */
    @ExcelProperty(value = "所属片区")
    private String zoneName;

    @ExcelProperty(value = "类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_plot_type")
    private String plotType;

    @ExcelProperty(value = "地块名称")
    private String plotName;

    @ExcelProperty(value = "状态", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_plot_status")
    private Integer plotStatus;

    @ExcelProperty(value = "是否租赁", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_yes_no")
    private Integer isLease;

    private String plotRemark;

    @ExcelProperty(value = "面积(亩)")
    private BigDecimal plotArea;

    private String plotLocationDesc;
    private BigDecimal plotLocationX;
    private BigDecimal plotLocationY;

    @ExcelProperty(value = "土壤类型", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_soil_type")
    private String soilType;

    @ExcelProperty(value = "土壤肥力", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_soil_fertility")
    private String soilFertility;

    @ExcelProperty(value = "PH")
    private BigDecimal soilPh;

    @ExcelProperty(value = "地势", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_terrain_condition")
    private String terrainCondition;

    @ExcelProperty(value = "光照", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_light_condition")
    private String lightCondition;

    @ExcelProperty(value = "排水", converter = ExcelDictConvert.class)
    @ExcelDictFormat(dictType = "djs_drain_condition")
    private String drainCondition;

    @ExcelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 当前作物名（service 层动态计算）。
     *
     * <p>D8 阶段 {@code t_plant_plant_details} 0 行，返回 null；D9+ PLT-PLAN-001 上线后启用 JOIN。</p>
     */
    private String currentCropName;
}
